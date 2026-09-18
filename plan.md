# Plan — Fix depth-model speed + accuracy on Samsung S22 (SD 8 Gen 1, HTP v69)

## Diagnosis (completed)

**Symptom:** `yolo26n-depth` at ~4 fps, imgsz=480.

**Why it's slow — the model runs on CPU, never the NPU/GPU.** The QNN EP
(`onnxruntime-android-qnn:1.28.0`, QAIRT SDK 2.42.0) cannot initialize on this device:

- **HTP (NPU):** `deviceCreate()` fails with `QNN_DEVICE_ERROR_INVALID_CONFIG`.
  Device firmware is SNPE-era (vendor ships `libSnpeHtpV69*`, **no QNN runtime
  anywhere** in /system, /vendor, /odm). Independent of `soc_model` and
  `ADSP_LIBRARY_PATH` (both tested on-device). ORT then compiles **zero** nodes
  and silently falls back to CPU; `createSession` doesn't throw, so the app logs
  a false "SUCCESS".
- **GPU (QNN/Adreno):** `QNN_COMMON_ERROR_PLATFORM_NOT_SUPPORTED` — the app
  linker namespace cannot dlopen `/vendor/lib64/libOpenCL.so`.

**Why INT8 made it worse — accuracy.** The first CPU speedup attempt used static
INT8 (QDQ). This is a Depth-Anything-style **DPT model** (transformer/attention
head + `Resize`/`ConvTranspose` upsampling) — exactly the ops INT8 *activation*
quantization destroys. Result: ~2x faster but depth output is noise. **INT8 is
the wrong tool for this model.** Reverting to FP32/FP16.

## Goal

Correct depth **and** higher fps, using **NCNN (FP16)**:
- **Vulkan** (Adreno 730) as the fast path — app-accessible, sidesteps both
  blockers above (no HTP firmware dep, no vendor-OpenCL linker wall).
- **CPU multi-core** fallback, `num_threads=4` (the X1 + 3×A78 perf cores).
- On-device **benchmark decides** which is primary.

## Steps

1. **Revert to FP32-ONNX now** (restore accuracy). One-line asset swap in
   `YoloDepthEstimator`. Deploy + verify sane depth (Min/Max in meters).
2. **Build NCNN:**
   - Host (macOS) build, `NCNN_BUILD_TOOLS=ON` → `onnx2ncnn`.
   - Android (NDK, `arm64-v8a`) build, `NCNN_VULKAN=ON` → `libncnn.so`.
3. **Convert model:** `onnx2ncnn yolo26n-depth.onnx → .param/.bin` in **FP16**
   (halves weight memory, near-FP32 accuracy).
4. **Integrate:**
   - Bundle `libncnn.so` (`app/src/main/jniLibs/arm64-v8a/`) + `.param/.bin`
     (assets).
   - JNI bridge (`NcnnDepth`): extract assets → load `ncnn::Net` →
     `opt.num_threads=4` → optional `create_gpu()` (Vulkan).
   - `YoloDepthEstimator` cascade: **NCNN Vulkan → NCNN CPU → ORT FP32**.
5. **Benchmark on-device:** time CPU vs Vulkan over N warm frames, log both,
   keep the faster as primary.
6. **Verify:** sane depth values, fps well above 4.

## Expected

- **Accuracy:** FP16 ≈ FP32 (no INT8 noise).
- **Speed:** NCNN CPU typically beats ORT CPU on ARM; Vulkan likely fastest
  (Ultralytics' own benchmarks show depth favors GPU over NPU).

## Risks / notes

- `onnx2ncnn` op coverage: model uses Conv, ConvTranspose, Resize, MatMul,
  Softmax, MaxPool, Clip, Exp, Pow, Sigmoid — all supported; confirm the
  conversion completes without dropped ops.
- Vulkan init can fail on some devices → CPU fallback covers it.
- Keep the INT8 asset for now; delete later if unused (saves ~5.6 MB APK).
- QNN path stays behind `USE_QNN=false` for devices with a working HTP runtime.
