import onnxruntime as ort
import onnxruntime_qnn

# Configure session options to generate and embed QNN context
sess_options = ort.SessionOptions()
sess_options.add_session_config_entry("ep.context_enable", "1")
sess_options.add_session_config_entry("ep.context_embed_mode", "1")  # Embeds binary inside ONNX
sess_options.add_session_config_entry("ep.context_file_path", "yolo26n-depth_ctx.onnx")

provider_options = [{
    "backend_path": onnxruntime_qnn.get_qnn_htp_path(),  # libQnnHtp.so on Linux
    "htp_performance_mode": "burst",
    "soc_model": "60"  # Snapdragon 8 Gen 1 (v69)
}]

# Initializing the session generates and writes yolo26n-depth_ctx.onnx
session = ort.InferenceSession(
    "yolo26n-depth_qnn.onnx",
    sess_options,
    providers=["QNNExecutionProvider"],
    provider_options=provider_options,
)
print("Successfully compiled and saved yolo26n-depth_ctx.onnx!")
