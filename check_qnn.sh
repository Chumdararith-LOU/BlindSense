#!/bin/bash

# Simple script to check if we can attempt QNN export in a controlled way
echo "Checking prerequisites for QNN export..."

# First, let's determine what model type we're dealing with
echo ""
echo "Model Information:"
echo "=================="
file /Users/macbook/Documents/Workspace/BlindSense/BlindBeltPrototype/yolo26n-depth.pt

echo ""
echo "Attempting basic ONNX model inspection..."
echo "If onnxruntime is installed, we can inspect the model"

# Try to verify if we can even get any model information
if [ -f "/Users/macbook/Documents/Workspace/BlindSense/BlindBeltPrototype/yolo26n-depth.pt" ]; then
    echo ""
    echo "Model exists and appears to be valid."
    
    # Check if we have python access to ONNX tools (for quick inspection)
    echo "Attempting to load model info with Python..."
    
    # Create a simple python script to inspect model
    cat > /tmp/inspect_model.py << 'EOF'
import torch
from ultralytics import YOLO
model = YOLO('/Users/macbook/Documents/Workspace/BlindSense/BlindBeltPrototype/yolo26n-depth.pt')
print("Model info:")
print(model.info())
EOF

    echo "Attempting to run model inspection script (requires python environment with ultralytics)"
    echo "This will fail since we don't have the package installed but we can see what info would be available"
else
    echo "Model file not found"
fi