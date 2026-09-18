FROM --platform=linux/amd64 python:3.11-slim

WORKDIR /workspace

RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    unzip \
    libgl1 \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir numpy opencv-python-headless onnx ultralytics
RUN pip install --no-cache-dir onnxruntime-qnn

COPY export_qnn.py .
COPY yolo26n-depth.pt .
COPY calib_data.yaml .
COPY calib_data ./calib_data

CMD ["python", "export_qnn.py"]