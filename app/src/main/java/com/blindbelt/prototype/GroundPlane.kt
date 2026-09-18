package com.blindbelt.prototype

data class GroundPlane(
    val width: Int,
    val height: Int,
    val rowMedianDepth: FloatArray,
    val isGroundRow: BooleanArray
)