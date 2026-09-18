from ultralytics import YOLO

model = YOLO("yolo26n-depth.pt")

model.export(
    format="qnn",
    name="69",
    imgsz=640,
    data="calib_data.yaml",
    workers=0,
)

print("QNN export complete. Look for yolo26n-depth_qnn.onnx")
