package com.example.autoenhancecamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CameraScreen() } }
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    appResolver = context.contentResolver
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Offline AI · Auto enhance · Portrait blur") }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Surface(Modifier.fillMaxSize(), color = ComposeColor(0xFF0B0B10)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("OBITO", color = ComposeColor(0xFFFF303F), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
            Text("OFFLINE AI CAMERA", color = ComposeColor(0xFFE4DCE0), style = MaterialTheme.typography.labelMedium)
            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                if (granted) AndroidView(factory = { ctx ->
                    val pv = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        val preview = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val ic = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build(); capture = ic
                        try { provider.unbindAll(); provider.bindToLifecycle(context as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, ic) }
                        catch (_: Exception) { message = "Camera চালু করা যায়নি" }
                    }, ContextCompat.getMainExecutor(ctx)); pv
                }, modifier = Modifier.fillMaxSize()) else Text("Camera permission প্রয়োজন", color = ComposeColor.White, modifier = Modifier.align(Alignment.Center))
            }
            Text(message, color = ComposeColor(0xFFE9D9B0), modifier = Modifier.padding(12.dp))
            Button(onClick = {
                val ic = capture ?: return@Button
                busy = true; message = "AI processing হচ্ছে…"
                val temp = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                ic.takePicture(ImageCapture.OutputFileOptions.Builder(temp).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        androidx.lifecycle.lifecycleScope // keep Activity lifecycle extension available in project
                        (context as ComponentActivity).lifecycleScope.launch {
                            try {
                                val enhanced = withContext(Dispatchers.IO) {
                                    val source = BitmapFactory.decodeFile(temp.absolutePath) ?: error("ছবি পড়া যায়নি")
                                    ObitoProcessor.process(context, source).also { source.recycle() }
                                }
                                withContext(Dispatchers.IO) { saveToGallery(enhanced) }
                                enhanced.recycle(); temp.delete()
                                message = "AI-enhanced ছবি সেভ হয়েছে ✓"
                                Toast.makeText(context, "Saved to Pictures/OBITO", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) { message = "Processing error: ${e.localizedMessage ?: "unknown"}" }
                            finally { busy = false; temp.delete() }
                        }
                    }
                    override fun onError(exception: ImageCaptureException) { busy = false; message = "ছবি তোলা যায়নি" }
                })
            }, enabled = granted && !busy, modifier = Modifier.padding(bottom = 24.dp).size(86.dp), shape = CircleShape) {
                Text(if (busy) "…" else "📸", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

private fun saveToGallery(bitmap: Bitmap) {
    val resolver = appResolver ?: return
    val name = "OBITO_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
    val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OBITO") }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Gallery save failed")
    resolver.openOutputStream(uri)?.use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it)) error("JPEG encode failed") }
}
private var appResolver: android.content.ContentResolver? = null

private object ObitoProcessor {
    private val segmenter by lazy { Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build()) }
    private val faceDetector by lazy { FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).build()) }

    fun process(context: Context, src: Bitmap): Bitmap {
        // Limit working dimensions for mobile memory and ML inference stability.
        val maxSide = 2048
        val work = if (max(src.width, src.height) > maxSide) {
            val ratio = maxSide.toFloat() / max(src.width, src.height)
            Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
        } else src
        val input = InputImage.fromBitmap(work, 0)
        // Bundled ML Kit models: segmentation and face detection run locally.
        val mask: SegmentationMask? = try { Tasks.await(segmenter.process(input)).getSegmentationMask() } catch (_: Exception) { null }
        val faces = try { Tasks.await(faceDetector.process(input)) } catch (_: Exception) { emptyList() }
        val denoised = edgeAwareDenoise(work)
        var enhanced = toneAndFaceEnhance(denoised, faces.map { it.boundingBox })
        // Optional neural restoration: executes locally when compatible model asset is installed.
        val restorer = try { GfpganRestorer(context) } catch (_: Exception) { null }
        if (restorer?.available == true && faces.isNotEmpty()) {
            for (face in faces) {
                val restored = try { restorer.restore(enhanced, face.boundingBox) } catch (_: Exception) { null }
                if (restored != null) { enhanced.recycle(); enhanced = restored }
            }
        }
        restorer?.close()
        if (work !== src) work.recycle()
        denoised.recycle()
        if (mask == null) return enhanced // fallback: no portrait blur if model fails
        val blurred = boxBlur(enhanced, radius = 12)
        val output = Bitmap.createBitmap(enhanced.width, enhanced.height, Bitmap.Config.ARGB_8888)
        val fg = IntArray(enhanced.width * enhanced.height); val bg = IntArray(fg.size)
        enhanced.getPixels(fg, 0, enhanced.width, 0, 0, enhanced.width, enhanced.height)
        blurred.getPixels(bg, 0, enhanced.width, 0, 0, enhanced.width, enhanced.height)
        val m = mask.buffer.asFloatBuffer(); val mw = mask.width; val mh = mask.height
        for (y in 0 until enhanced.height) for (x in 0 until enhanced.width) {
            val mx = (x * mw / enhanced.width).coerceIn(0, mw - 1); val my = (y * mh / enhanced.height).coerceIn(0, mh - 1)
            val confidence = m.get(my * mw + mx).coerceIn(0f, 1f)
            // Soft edge blend keeps a natural outline and avoids hard cut-outs.
            val a = ((confidence - 0.18f) / 0.72f).coerceIn(0f, 1f)
            val i = y * enhanced.width + x
            fg[i] = blend(bg[i], fg[i], a)
        }
        output.setPixels(fg, 0, enhanced.width, 0, 0, enhanced.width, enhanced.height)
        mask.buffer.rewind(); enhanced.recycle(); blurred.recycle()
        return output
    }

    // Fast, edge-aware 3x3 smoothing reduces chroma speckle while protecting strong edges.
    private fun edgeAwareDenoise(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height; val pixels = IntArray(w*h); src.getPixels(pixels,0,w,0,0,w,h)
        val out = pixels.copyOf()
        for (y in 1 until h-1) for (x in 1 until w-1) {
            val idx = y*w+x; val center = pixels[idx]; val cr=Color.red(center); val cg=Color.green(center); val cb=Color.blue(center)
            var sr=0f; var sg=0f; var sb=0f; var total=0f
            for (dy in -1..1) for (dx in -1..1) {
                val c=pixels[(y+dy)*w+x+dx]; val dr=Color.red(c)-cr; val dg=Color.green(c)-cg; val db=Color.blue(c)-cb
                val diff=abs(dr)+abs(dg)+abs(db); val weight=if(dx==0&&dy==0) 1f else (1f-diff/100f).coerceAtLeast(0f)*0.22f
                sr+=Color.red(c)*weight; sg+=Color.green(c)*weight; sb+=Color.blue(c)*weight; total+=weight
            }
            out[idx]=Color.rgb((sr/total).toInt(),(sg/total).toInt(),(sb/total).toInt())
        }
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { it.setPixels(out,0,w,0,0,w,h) }
    }

    private fun toneAndFaceEnhance(src: Bitmap, faces: List<android.graphics.Rect>): Bitmap {
        val w=src.width; val h=src.height; val p=IntArray(w*h); src.getPixels(p,0,w,0,0,w,h)
        for (y in 0 until h) for (x in 0 until w) {
            val i=y*w+x; val c=p[i]; var r=Color.red(c).toFloat(); var g=Color.green(c).toFloat(); var b=Color.blue(c).toFloat()
            val lum=.299f*r+.587f*g+.114f*b
            val face=faces.any { x>=it.left && x<it.right && y>=it.top && y<it.bottom }
            val contrast=if(face) 1.035f else 1.055f
            r=(r-127.5f)*contrast+130f; g=(g-127.5f)*contrast+130f; b=(b-127.5f)*contrast+130f
            val l=.299f*r+.587f*g+.114f*b
            val sat=if(face)1.025f else 1.045f
            r=l+(r-l)*sat; g=l+(g-l)*sat; b=l+(b-l)*sat
            // Face-area gentle exposure lift only: no facial reshaping or synthetic detail.
            if(face){ r+=2.5f; g+=2.5f; b+=2.5f }
            p[i]=Color.rgb(r.toInt().coerceIn(0,255),g.toInt().coerceIn(0,255),b.toInt().coerceIn(0,255))
        }
        return Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { it.setPixels(p,0,w,0,0,w,h) }
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        // Downscale before blur for a fast, lens-like background blur, then upscale.
        val scale=4; val sw=max(1,src.width/scale); val sh=max(1,src.height/scale)
        val small=Bitmap.createScaledBitmap(src,sw,sh,true); val r=max(2,radius/scale)
        val p=IntArray(sw*sh); small.getPixels(p,0,sw,0,0,sw,sh); small.recycle()
        val tmp=IntArray(p.size); val out=IntArray(p.size)
        for(y in 0 until sh) for(x in 0 until sw){ var sr=0;var sg=0;var sb=0;var n=0
            for(k in -r..r){val xx=(x+k).coerceIn(0,sw-1);val c=p[y*sw+xx];sr+=Color.red(c);sg+=Color.green(c);sb+=Color.blue(c);n++};tmp[y*sw+x]=Color.rgb(sr/n,sg/n,sb/n)
        }
        for(y in 0 until sh) for(x in 0 until sw){var sr=0;var sg=0;var sb=0;var n=0
            for(k in -r..r){val yy=(y+k).coerceIn(0,sh-1);val c=tmp[yy*sw+x];sr+=Color.red(c);sg+=Color.green(c);sb+=Color.blue(c);n++};out[y*sw+x]=Color.rgb(sr/n,sg/n,sb/n)
        }
        val blurredSmall=Bitmap.createBitmap(sw,sh,Bitmap.Config.ARGB_8888).also{it.setPixels(out,0,sw,0,0,sw,sh)}
        return Bitmap.createScaledBitmap(blurredSmall,src.width,src.height,true).also{blurredSmall.recycle()}
    }
    private fun blend(bg:Int,fg:Int,a:Float):Int=Color.rgb((Color.red(bg)*(1-a)+Color.red(fg)*a).toInt(),(Color.green(bg)*(1-a)+Color.green(fg)*a).toInt(),(Color.blue(bg)*(1-a)+Color.blue(fg)*a).toInt())
}
