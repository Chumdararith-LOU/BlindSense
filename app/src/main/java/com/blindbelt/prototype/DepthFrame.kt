package com.blindbelt.prototype

data class DepthFrame(
    val width: Int,
    val height: Int,
    val timestampMs: Long,
    val depth: FloatArray
)