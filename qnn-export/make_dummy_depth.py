import numpy as np
import cv2
from pathlib import Path

img_dir = Path("calib_data/images")
depth_dir = Path("calib_data/depth")
depth_dir.mkdir(parents=True, exist_ok=True)

jpgs = sorted(img_dir.glob("*.jpg"))
print(f"Found {len(jpgs)} images")

removed = 0
kept = []
for jpg in jpgs:
    img = cv2.imread(str(jpg))  # BGR, HxWx3 if valid
    if img is None or img.ndim != 3 or img.shape[2] != 3:
        jpg.unlink()
        removed += 1
        continue
    h, w = img.shape[:2]
    dummy_depth = np.full((h, w), 5.0, dtype=np.float32)
    # Same stem as the image, but written into the sibling depth/ folder
    # (NOT next to the image) so it never collides with Ultralytics'
    # own image disk-cache .npy files.
    np.save(depth_dir / jpg.with_suffix(".npy").name, dummy_depth)
    kept.append(jpg)

print(f"Removed {removed} non-3-channel images (cv2-decoded check)")
print(f"Done. Generated {len(kept)} placeholder .npy depth maps in {depth_dir}/")