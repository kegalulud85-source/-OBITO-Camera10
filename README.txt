OBITO optional neural face restoration model

The Android pipeline now attempts GfpganRestorer after ML Kit face detection,
when a compatible model asset is present. Missing/incompatible model safely falls
back to regular enhancement.

Required asset filename: gfpgan_fp16_inline.tflite
Required tensor contract in current adapter: input/output [1,3,512,512], RGB,
float32 normalized [-1,1], NCHW. The crop is currently square-resized, not full
5-point FFHQ landmark aligned; this is a prototype and may alter identity/details.

A compatible model must be obtained/conversion-validated separately. Do not rename
an ONNX or .pth file to .tflite: formats are not interchangeable.
Potential model reference (check exact format and license before use):
https://huggingface.co/NarinAhir/GFPGAN-v1.4-TFLite
Official upstream weights and license:
https://github.com/TencentARC/GFPGAN

Large model files are intentionally not included in this ZIP. Confirm model input
and output tensors using a test harness before distributing or enabling it.
