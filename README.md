# BlindBelt Prototype

**An AI-powered, real-time monocular-depth navigation aid for the visually impaired.**
A camera sees the world, a phone runs a depth-estimation neural network, and a belt of
vibration motors tells the wearer — hands-free, eyes-free — where the safe walking
corridor is.

> Status: **Phase A complete.** End-to-end chain verified: ESP32-CAM eyes → Android
> brain → UDP v2 protocol → belt firmware (motors pending arrival). On-screen Belt
> Simulator widget + belt serial log act as stand-ins for the physical motors.

---

## 1. System overview

```
 ┌──────────────┐  MJPEG over HTTP (:81/stream)   ┌───────────────────────────┐
 │  ESP32-CAM   │ ──────────────────────────────▶ │  ANDROID PHONE (the brain)│
 │  (OV2640)    │                                 │                           │
 └──────────────┘                                 │  frames → YOLO26-Depth    │
        (or the phone's own camera in             │  (ONNX Runtime, CPU,      │
         standalone mode)                         │   480×480, ~3–4 Hz)       │
                                                  │         │                 │
                                                  │  ground / obstacle /      │
                                                  │  drop-off / zone analysis │
                                                  │         │                 │
                                                  │  PathFinder → decision    │
                                                  │         │                 │
                                                  │  BeltSimulatorView (UI)   │
                                                  │  + phone haptics (backup) │
                                                  └────────────┬──────────────┘
                                                               │ UDP port 8888
                                                               │ 4-byte packet v2
                                                  ┌────────────▼──────────────┐
                                                  │  BELT ESP32 (the muscles) │
                                                  │  pattern engine, watchdog │
                                                  │  → LEFT / RIGHT motors    │
                                                  └───────────────────────────┘
```

**Design philosophy — the haptic corridor.** The belt never says "danger there";
it says **"go here"**. A pulsing motor means *steer toward that side*; silence means
*you are inside the safe corridor, walk straight*. Pulse **rate** encodes urgency
(closer obstacle = faster pulses), amplitude is fixed and strong. The wearer learns
to "seek the silence".

---

## 2. Repository layout

```
BlindBeltPrototype/
├── app/                          Android application (Kotlin)
│   └── src/main/
│       ├── assets/yolo26n-depth.onnx   YOLO26n-depth, 480×480 input (bundled in APK)
│       └── java/com/blindbelt/prototype/
│           ├── MainActivity.kt         wiring, camera/MJPEG source switch, UI callbacks
│           ├── MjpegStreamReader.kt    ESP32-CAM MJPEG decoder (decoupled, latest-frame-wins)
│           ├── FrameProcessor.kt       per-frame pipeline orchestration + timers
│           ├── YoloDepthEstimator.kt   ONNX Runtime inference (CPU EP), reusable buffers
│           ├── GroundPlaneEstimator.kt per-row ground model
│           ├── ObstacleDetector.kt     ground-aware obstacle rule (calibrated, see §8)
│           ├── DropOffDetector.kt      negative-obstacle (stairs/ditch) heuristic
│           ├── ZoneDepthAnalyzer.kt    left/center/right corridor averages
│           ├── PathFinder.kt           decision: CLEAR / STEER_L / STEER_R / STOP
│           ├── HapticFeedbackManager.kt phone-vibrator mirror of belt patterns
│           ├── UdpHapticSender.kt      protocol v2 encoder + sender (CMD/BAND)
│           ├── BeltSimulatorView.kt    on-screen two-disc belt simulator (no-motor dev mode)
│           ├── DepthOverlayView.kt     heatmap + zone tints + direction arrow
│           ├── ImuTracker.kt           accelerometer/gyro tracking (reserved for Phase B)
│           └── AudioAlertManager.kt    optional audio mirror of decisions
├── belt_motors/belt_motors.ino   Belt ESP32 firmware: UDP v2 parser, pattern
│                                 engine, 1200 ms watchdog, serial logging
├── esp32-cam/cam_streamer.ino    ESP32-CAM MJPEG streamer (multipart /stream on :81)
├── tune_pipeline.py              OFFLINE CALIBRATION: replays video frames through the
│                                 real ONNX model and sweeps detector parameters
├── extract_frames.py             cv2-based frame extractor (ffmpeg fallback)
├── export_model.py               helper to (re-)export the ONNX model from Ultralytics
├── udp_listener.py               Mac-side UDP belt simulator / packet sniffer
├── qnn-export/                   Docker tooling for Qualcomm QNN (Hexagon NPU) exports
│                                 (experiment; see §10 — parked, reproducible)
├── plan.md                       project phase plan and history
└── README.md                     you are here
```

---

## 3. How the brain works (frame → decision)

All of this runs per processed frame (~3–4 Hz by design, see §9):

1. **Frame source** (`MainActivity.kt`, `USE_MJPEG_SOURCE` flag)
   * `false` (default): phone's native CameraX feed — standalone mode, zero hardware.
   * `true`: `MjpegStreamReader` pulls multipart JPEG from the ESP32-CAM.
     Decoding is **decoupled** from inference (latest-frame-wins queue): the stream is
     drained at 15–25 FPS while inference consumes the newest frame at its own cadence.
2. **Depth estimation** (`YoloDepthEstimator.kt`)
   * YOLO26n-depth, ONNX Runtime **CPU execution provider**, input `1×3×480×480`,
     pixels normalized `/255`, NCHW.
   * Output: dense depth map (meters, unbounded log-depth head) → `DepthFrame`.
   * Synchronized methods + preallocated buffers: one inference at a time, zero GC churn.
3. **Ground plane** (`GroundPlaneEstimator.kt`): per-row median depths, ground-row mask.
4. **Obstacles** (`ObstacleDetector.kt`) — *ground-aware rule*, calibrated offline (§8):
   a pixel is an obstacle iff `0 < depth < rowRef[row] − 0.5 m` **and** `depth < 2.0 m`,
   where `rowRef` = 75th percentile of that row; an obstacle "exists" only if such
   pixels exceed **1 %** of the frame. This stops the road/floor itself from counting
   as a wall — the single most important tuning decision in the project.
5. **Drop-offs** (`DropOffDetector.kt`): sudden far-jumps between row medians
   (stairs/ditch heuristic; refinement is Phase B, see §11).
6. **Zones** (`ZoneDepthAnalyzer.kt`): mean depth of left / center / right thirds.
7. **PathFinder** (`PathFinder.kt`), in priority order:
   | Condition | Decision |
   |---|---|
   | drop-off detected | `STOP`, danger HIGH |
   | obstacle AND **no passable zone** (best zone avg < 1.5 m) | `STOP`, danger HIGH (wall everywhere → don't steer into a wall slice) |
   | obstacle AND some zone passable | steer to zone with **largest** average, danger MEDIUM |
   | otherwise | `CENTER`, danger LOW (corridor clear) |
8. **Output**: decision → `UdpHapticSender` (belt), `BeltSimulatorView` (screen),
   `HapticFeedbackManager` (phone vibrator), `DepthOverlayView` (heatmap/arrow).

### Haptic vocabulary (identical on belt, widget, and phone vibrator)

| Situation | Sensation |
|---|---|
| Corridor clear | silence (packets still flowing!) |
| Steer left/right, far band (≥1.5 m) | that side pulses every **1200 ms** |
| Steer left/right, mid band (1.0–1.5 m) | pulses every **600 ms** |
| Steer left/right, near band (<1.0 m) | pulses every **250 ms** |
| STOP (wall ahead / drop-off) | both motors **continuous** |
| DROP (stairs) | both motors double-pulse, 1 Hz |
| FAULT / link lost | slow alternating L/R (never silence!) |

---

## 4. Belt protocol v2 (UDP, port 8888)

4-byte datagram, sent at decision cadence (~3–4 Hz):

| Byte | Meaning |
|---|---|
| 0 | `0xA5` sync |
| 1 | CMD: `0`=CLEAR, `1`=STEER_LEFT, `2`=STEER_RIGHT, `3`=STOP, `4`=DROP, `5`=FAULT, `6`=EDGE |
| 2 | BAND: `0`=far, `1`=mid, `2`=near (from nearest danger depth: ≥1.5 m→0, <1.0 m→2, else 1) |
| 3 | checksum = `0xA5 ^ CMD ^ BAND` |

**Safety rules (non-negotiable):**
* The belt expects a valid packet at least every **1200 ms**. If packets stop
  (app killed, Wi-Fi dead), the belt **self-generates FAULT** (alternating pulses).
  *Silence must only ever mean "packets are flowing and the path is clear".*
* Belt prints every state change to Serial (`[BELT] STEER_LEFT band=2`) — the serial
  monitor is a first-class debugging witness.
* Boot self-test: both motors pulse 250 ms at power-on.

---

## 5. Belt hardware & firmware

* Firmware: `belt_motors/belt_motors.ino` (Arduino-ESP32 core 3.x API: `ledcAttach(pin,…)`).
* Motors on **GPIO 26 (L)** and **27 (R)** through logic-level MOSFETs.
  **Never power motors from the ESP32 3V3 pin** — use a separate LiPo rail;
  add a 100–220 µF cap per motor rail and flyback diodes, or brownouts will reset the MCU.
* Fill `SSID`/`PASS` (phone hotspot), flash (board: *ESP32 Dev Module*), read the IP
  from Serial Monitor → this is `UDP_HOST` in the app.

### ESP32-CAM (the eyes)
* Firmware: `esp32-cam/cam_streamer.ino` — QVGA JPEG, multipart `/stream` on **port 81**.
* Arduino board: *AI Thinker ESP32-CAM*, Partition Scheme *Huge APP*, PSRAM enabled.
* With a CAM-MB dock, upload works directly; otherwise hold GPIO0 low during boot.
* Read its IP from Serial → `STREAM_URL = "http://<IP>:81/stream"`.
* **The phone hotspot must be 2.4 GHz** — ESP32 has no 5 GHz radio.

---

## 6. Quick start — software only (no hardware)

1. Install Android Studio (with JDK 17) and clone this repo.
2. Open the project; let Gradle sync (the ONNX model is bundled in `app/src/main/assets`).
3. Run on any **Android 10+ (API 29)** phone; grant Camera permission.
4. Point the phone around: heatmap + direction arrow + **Belt Simulator widget**
   (two discs, bottom of screen) show exactly what a physical belt would feel.
   Phone vibrator mirrors the patterns.

## 7. Full hardware setup

1. Phone: enable hotspot (2.4 GHz).
2. Flash `esp32-cam/cam_streamer.ino` with hotspot credentials → note `[CAM] IP`.
   Verify `http://<CAM_IP>:81/stream` in the phone browser.
3. Flash `belt_motors/belt_motors.ino` with hotspot credentials → note `[BELT] IP`.
4. In the app: `USE_MJPEG_SOURCE = true`, `STREAM_URL = "http://<CAM_IP>:81/stream"`,
   `UDP_HOST = "<BELT_IP>"` → rebuild & install.
5. **Three-witness test** (widget / belt serial / scene):
   | Scene | Expected |
   |---|---|
   | open room | widget `CLEAR`, belt `[BELT] CLEAR band=0` |
   | cover cam's right half | `STEER LEFT`, `[BELT] STEER_LEFT band=2` |
   | cover cam's left half | `STEER RIGHT` |
   | block lens close-up | `STOP`, both discs solid |
   | kill the app | within 1.2 s: `[BELT] WATCHDOG -> FAULT` |

---

## 8. Performance & design decisions

* **~3–4 Hz decision rate is intentional.** Human walking ≈1.4 m/s; a distinct pulse
  every 250–1200 ms is readable, battery-friendly, and thermally safe. Chasing 30 FPS
  would blur pulses into a constant buzz.
* CPU ONNX at 480×480 ≈ 300 ms/frame on Snapdragon 8 Gen 1 — acceptable for the above.
* MJPEG decode is decoupled (15–25 FPS) so the stream never waits on inference.
* NNAPI/QNN/NCNN paths were explored and **deliberately parked**: standard
  `onnxruntime-android` ships no QNN EP, and native experiments caused segfaults.
  Reproducible QNN export lives in `qnn-export/` for a future revisit.

## 9. Known limitations & roadmap

* Drop-off/curb detection is a naive row-jump heuristic → **Phase B**: IMU-pitch ground
  model, 2-frame temporal confirmation, EDGE (0.2–0.5 m) vs DROP (>0.5 m) bands.
* OV2640 is blind at night → IR illuminator or brightness-based fallback to phone cam.
* IPs are DHCP-assigned per boot → static-IP pinning planned.
* Portrait/landscape UI validation, absolute-scale calibration, belt telemetry heartbeat.

## 10. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| ESP32 prints dots forever | hotspot not 2.4 GHz, or SSID/pass typo |
| Belt spams `WATCHDOG -> FAULT` | app not sending: wrong `UDP_HOST`, app backgrounded (Android kills it — keep screen on), or protocol mismatch |
| Black preview in MJPEG mode | wrong `STREAM_URL`; test in browser first |
| `ORT_INVALID_ARGUMENT … Expected: 480` | model `imgsz` ≠ `INPUT_SIZE` in `YoloDepthEstimator.kt` — they must match |
| No phone vibration on clear path | by design: CENTER = silence |
| App dies when backgrounded | normal Android behavior; dev note: `adb shell svc power stayon true` |
