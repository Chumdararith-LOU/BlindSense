# BlindBelt Prototype

An AI-powered, real-time depth estimation and haptic navigation system for the visually impaired.

## How it works

Uses YOLO26-Depth via ONNX Runtime to process camera frames, calculates safe walking corridors, and outputs steering commands (via UDP to a physical belt, or via the on-screen Belt Simulator widget).

## How to run

1. Open in Android Studio.
2. Run on any Android 10+ (API 29) device.
3. Grant Camera permissions.

## Hardware Note

By default, the app uses the phone's native camera. To use with the ESP32-CAM hardware, change `USE_MJPEG_SOURCE` to `true` in `MainActivity.kt` and update the `STREAM_URL` constant in `MjpegStreamReader.kt` and the `UDP_HOST` constant in `UdpHapticSender.kt`.
