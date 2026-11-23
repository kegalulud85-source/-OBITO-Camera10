# OBITO Offline AI Camera (prototype)

Android camera prototype with offline image processing: automatic tone correction, edge-aware smoothing, ML Kit face detection, selfie segmentation portrait blur, gallery saving to `Pictures/OBITO`, and an optional TensorFlow Lite face-restoration adapter.

## Important status
- Source project only; no APK has been built or device-tested in this workspace.
- The optional GFPGAN adapter expects a compatible model asset, but no neural weights are included. A legally obtained compatible model must be placed at `app/src/main/assets/gfpgan_fp16_inline.tflite` after its tensor contract is confirmed.
- Adapter assumptions: float32 NCHW RGB `[1,3,512,512]`, output `[-1,1]`; not yet validated with actual weights.
- Face crop is padded and softly blended; 5-point landmark/FFHQ alignment is not yet implemented.
- If the model is missing or errors, conventional enhancement/portrait blur fallback is used.

## Build APK from a phone using GitHub Actions
Read `BUILD_APK_FROM_PHONE_BN.md`. A GitHub Actions workflow is included at `.github/workflows/build-apk.yml`; after uploading the project to a GitHub repository, run the workflow and download the `OBITO-debug-apk` artifact.

## Local build
Open the project in Android Studio with JDK 17, Gradle 8.9, and Android SDK 35; run `gradle assembleDebug`.
