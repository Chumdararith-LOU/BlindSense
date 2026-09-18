import os
import cv2

src = "calibrate.mp4"
out_dir = "calib_frames"
os.makedirs(out_dir, exist_ok=True)

cap = cv2.VideoCapture(src)
fps = cap.get(cv2.CAP_PROP_FPS)
step = max(1, int(round(fps * 0.5)))
idx = 0
n = 0
while True:
    ok, frame = cap.read()
    if not ok:
        break
    if idx % step == 0:
        cv2.imwrite(os.path.join(out_dir, f"frame_{n:04d}.jpg"), frame)
        n += 1
    idx += 1
cap.release()
print(f"saved {n} frames (fps={fps}, step={step})")
