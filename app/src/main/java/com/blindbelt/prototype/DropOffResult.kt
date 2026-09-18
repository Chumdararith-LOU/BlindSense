package com.blindbelt.prototype

data class DropOffResult(
    val dropOffRows: BooleanArray,
    val dropOffCount: Int,
    val maxDropOffDepth: Float
)