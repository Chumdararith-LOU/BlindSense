#!/bin/bash
set -e  # stop on first failure instead of silently continuing to the export step

# Setup and run QNN export for YOLO model
echo "Setting up QNN export environment..."

# Install dependencies (if not already installed)
pip install torch torchvision ultralytics onnx numpy opencv-python-headless

# Create calibration directories - images and depth are siblings, never merged
mkdir -p calib_data/images calib_data/depth

# Extract calibration images if they don't exist
if [ ! -f "calib_data/images/000000000001.jpg" ]; then
    echo "Extracting calibration images from val2017.zip..."
    tmp_dir=$(mktemp -d)
    unzip -o -q /Users/macbook/Documents/Workspace/BlindSense/val2017.zip -d "$tmp_dir"
    find "$tmp_dir" -name "*.jpg" | head -200 | xargs -I {} mv {} calib_data/images/
    rm -rf "$tmp_dir"  # don't leave the other ~4800 extracted images lying around
fi

echo "Generating dummy depth maps..."
python make_dummy_depth.py

echo "Running QNN export..."
python export_qnn.py

echo "QNN export complete!"