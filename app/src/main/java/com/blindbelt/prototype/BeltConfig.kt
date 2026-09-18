package com.blindbelt.prototype

object BeltConfig {
    // ===== ASSEMBLER: EDIT ONLY THESE THREE LINES =====
    const val USE_MJPEG_SOURCE = false  // true = ESP32-CAM eyes
    const val STREAM_URL = "http://CAM_IP:81/stream"
    const val UDP_HOST = "BELT_IP"
    const val UDP_PORT = 8888
}
