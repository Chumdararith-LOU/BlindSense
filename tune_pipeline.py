import glob, sys, math, os
import cv2
import numpy as np
import onnxruntime as ort

MODEL_PATH = "app/src/main/assets/yolo26-depth.onnx"
frames_dir = sys.argv[1] if len(sys.argv) > 1 else "calib_frames"
FRAMES = sorted(glob.glob(frames_dir + "/*.jpg")) + sorted(glob.glob(frames_dir + "/*.png"))
assert FRAMES, "no frames found"

sess = ort.InferenceSession(MODEL_PATH, providers=["CPUExecutionProvider"])
meta = sess.get_inputs()[0]
H, W = meta.shape[2], meta.shape[3]
print(f"model input: {meta.name} {meta.shape} | frames: {len(FRAMES)}")

def infer(path):
    img = cv2.cvtColor(cv2.imread(path), cv2.COLOR_BGR2RGB)
    img = cv2.resize(img, (W, H))
    x = (img.astype(np.float32) / 255.0).transpose(2, 0, 1)[None]
    return np.array(sess.run(None, {meta.name: x})[0]).reshape(H, W)

if len(sys.argv) > 2:
    calib_path = sys.argv[2]
    pairs = []
    with open(calib_path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            name, dist = line.split()
            pairs.append((os.path.join(frames_dir, name), float(dist)))
    assert pairs, "no calib entries"
    print(f"scale calibration: {len(pairs)} entries from {calib_path}")
    pts = []
    for path, true_d in pairs:
        d = infer(path)
        pos = d[d > 0]
        est = float(np.median(pos)) if pos.size else float("nan")
        dev = 100.0 * (est - true_d) / true_d
        print(f"{os.path.basename(path)}: true={true_d:.2f} model={est:.2f} dev={dev:+.1f}%")
        pts.append((est, true_d))
    if len(pts) >= 2:
        x = np.array([p[0] for p in pts])
        y = np.array([p[1] for p in pts])
        a, b = np.linalg.lstsq(np.vstack([x, np.ones_like(x)]).T, y, rcond=None)[0]
        print(f"fit: true = {a:.4f} * model + {b:.4f}")
    sys.exit(0)

def decide(d, thresh, margin, min_frac, drop_jump):
    h, w = d.shape
    row_ref = np.percentile(d, 75, axis=1)
    obs_mask = (d < row_ref[:, None] - margin) & (d < thresh)
    obs = obs_mask.sum() > min_frac * h * w
    nearest = float(d[obs_mask].min()) if obs else math.inf
    jumps = np.diff(row_ref)
    lo, hi = int(0.4 * h), int(0.9 * h)
    drop = bool(jumps[lo:hi].max() > drop_jump) if hi > lo else False
    z = [d[:, :w//3].mean(), d[:, w//3:2*w//3].mean(), d[:, 2*w//3:].mean()]
    if drop:  return "STOP", nearest
    if obs:   return ["LEFT", "CENTER", "RIGHT"][int(np.argmax(z))], nearest
    return "CLEAR", nearest

print("running inference on all frames...")
depths = [infer(p) for p in FRAMES]

DROP_JUMP = 0.8
print(f"\n{'thresh':>6} {'margin':>6} {'minpix':>6} | {'obs%':>5} {'stop%':>5} {'steer%':>6} {'medNear':>7}")
for t in (1.5, 2.0, 2.5, 3.0):
    for m in (0.3, 0.5, 0.8):
        for f in (0.005, 0.01, 0.02):
            res = [decide(d, t, m, f, DROP_JUMP) for d in depths]
            n = len(res)
            obs_pct  = 100 * sum(1 for r in res if r[0] != "CLEAR") / n
            stop_pct = 100 * sum(1 for r in res if r[0] == "STOP") / n
            steer    = 100 * sum(1 for r in res if r[0] in ("LEFT", "RIGHT")) / n
            nears = [r[1] for r in res if r[1] != math.inf]
            med = f"{np.median(nears):.2f}" if nears else "-"
            print(f"{t:>6} {m:>6} {f:>6} | {obs_pct:>5.1f} {stop_pct:>5.1f} {steer:>6.1f} {med:>7}")
