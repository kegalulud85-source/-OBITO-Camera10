package com.example.autoenhancecamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/** Optional offline GFPGAN v1.4 TFLite restoration. Requires a compatible model asset.
 * Model contract: [1,3,512,512] RGB NCHW float [-1,1] -> same.
 */
internal class GfpganRestorer(context: Context) : AutoCloseable {
    private val interpreter: Interpreter? = try {
        val fd = context.assets.openFd("gfpgan_fp16_inline.tflite")
        val mapped = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        Interpreter(mapped, Interpreter.Options().apply { setNumThreads(2) })
    } catch (_: Exception) { null }

    val available: Boolean get() = interpreter != null

    /** Restores a padded square face crop; blends softly to avoid visible patch edges.
     * Landmark-based alignment is not yet implemented: this is geometric face-box alignment.
     */
    fun restore(bitmap: Bitmap, face: Rect): Bitmap? {
        val tflite = interpreter ?: return null
        val side = (max(face.width(), face.height()) * 1.65f).toInt().coerceAtLeast(64)
        val cx = (face.left + face.right) / 2f; val cy = (face.top + face.bottom) / 2f
        val left = (cx - side / 2f).toInt().coerceAtLeast(0); val top = (cy - side / 2f).toInt().coerceAtLeast(0)
        val right = (left + side).coerceAtMost(bitmap.width); val bottom = (top + side).coerceAtMost(bitmap.height)
        val cropW = right-left; val cropH = bottom-top
        if (cropW < 32 || cropH < 32) return null
        val crop = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        // Center square canvas prevents aspect distortion at image edges.
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(square)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(crop, (left-(cx-side/2f)).toFloat(), (top-(cy-side/2f)).toFloat(), null)
        val inputBitmap = Bitmap.createScaledBitmap(square, 512, 512, true)
        val input = ByteBuffer.allocateDirect(1*3*512*512*4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(512*512); inputBitmap.getPixels(pixels,0,512,0,0,512,512)
        for (channel in 0..2) for (pixel in pixels) { val v = when(channel) { 0 -> android.graphics.Color.red(pixel); 1 -> android.graphics.Color.green(pixel); else -> android.graphics.Color.blue(pixel) }; input.putFloat(v/127.5f-1f) }
        input.rewind()
        val output = ByteBuffer.allocateDirect(1*3*512*512*4).order(ByteOrder.nativeOrder())
        tflite.run(input, output); output.rewind()
        val restoredPixels = IntArray(512*512)
        val channels = Array(3) { FloatArray(512*512) }
        for (c in 0..2) for (i in channels[c].indices) channels[c][i] = ((output.float+1f)*127.5f).coerceIn(0f,255f)
        for(i in restoredPixels.indices) restoredPixels[i]=android.graphics.Color.rgb(channels[0][i].toInt(),channels[1][i].toInt(),channels[2][i].toInt())
        val restored = Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).also { it.setPixels(restoredPixels,0,512,0,0,512,512) }
        val patchSquare = Bitmap.createScaledBitmap(restored,side,side,true)
        val patch = Bitmap.createBitmap(patchSquare, left-(cx-side/2f).toInt(), top-(cy-side/2f).toInt(), cropW, cropH)
        val originalPixels=IntArray(cropW*cropH); crop.getPixels(originalPixels,0,cropW,0,0,cropW,cropH)
        val patchPixels=IntArray(cropW*cropH); patch.getPixels(patchPixels,0,cropW,0,0,cropW,cropH)
        val blended=IntArray(originalPixels.size)
        val feather=max(12,min(cropW,cropH)/8)
        for(y in 0 until cropH) for(x in 0 until cropW){
            val edge=min(min(x,cropW-1-x),min(y,cropH-1-y))
            val a=(edge.toFloat()/feather).coerceIn(0f,1f)*0.72f
            val i=y*cropW+x; val o=originalPixels[i]; val r=patchPixels[i]
            blended[i]=android.graphics.Color.rgb((android.graphics.Color.red(o)*(1-a)+android.graphics.Color.red(r)*a).toInt(),(android.graphics.Color.green(o)*(1-a)+android.graphics.Color.green(r)*a).toInt(),(android.graphics.Color.blue(o)*(1-a)+android.graphics.Color.blue(r)*a).toInt())
        }
        val result=bitmap.copy(Bitmap.Config.ARGB_8888,true)
        android.graphics.Canvas(result).drawBitmap(Bitmap.createBitmap(blended,cropW,cropH,Bitmap.Config.ARGB_8888),left.toFloat(),top.toFloat(),null)
        crop.recycle(); square.recycle(); inputBitmap.recycle(); restored.recycle(); patchSquare.recycle(); patch.recycle()
        return result
    }
    override fun close() { interpreter?.close() }
}
