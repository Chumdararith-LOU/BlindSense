package com.blindbelt.prototype

data class PathDecision(
    val direction: Direction,
    val dangerLevel: DangerLevel,
    val nearestDangerDepth: Float
)