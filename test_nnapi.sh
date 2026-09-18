#!/bin/bash

# Test script for NNAPI flag diagnostics on YOLO depth estimator
echo "Testing current implementation..."

# First check what files exist
echo "Checking model files:"
ls -la /Users/macbook/Documents/Workspace/BlindSense/BlindBeltPrototype/app/src/main/assets/

echo ""
echo "Current YoloDepthEstimator.kt NNAPI usage:"
grep -n "addNnapi\|NNAPI_FLAG" /Users/macbook/Documents/Workspace/BlindSense/BlindBeltPrototype/app/src/main/java/com/blindbelt/prototype/YoloDepthEstimator.kt

echo ""
echo "Current build.gradle ONNX Runtime usage:"
grep -n "onnxruntime-android" /Users/macbook/Documents/Workspace/BlindSense/BlindBeltPrototype/app/build.gradle