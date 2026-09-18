import os
import sys
import shutil
from pathlib import Path

# Check if ultralytics is installed
try:
    from ultralytics import YOLO
except ImportError:
    print("Please run: pip3 install ultralytics")
    sys.exit(1)

# Load the model
model = YOLO('yolo26n-depth.pt')

# Export to ONNX
model.export(format='onnx', imgsz=320, simplify=True, opset=12)

# Find the generated .onnx file in current directory
onnx_file = None
current_dir = Path('.')
for file in current_dir.iterdir():
    if file.is_file() and file.suffix == '.onnx':
        onnx_file = file
        break

if onnx_file is None:
    print("Error: Could not find the generated ONNX file")
    sys.exit(1)

# Create assets directory if it doesn't exist
assets_dir = Path('app/src/main/assets')
assets_dir.mkdir(parents=True, exist_ok=True)

# Move the ONNX file to assets directory
destination = assets_dir / "yolo26-depth.onnx"
shutil.move(str(onnx_file), str(destination))

print("Successfully exported YOLO26-Depth model to app/src/main/assets/yolo26-depth.onnx")