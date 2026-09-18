package com.blindbelt.prototype

data class ObstacleResult(
    val obstacleRows: BooleanArray,
    val obstacleCount: Int,
    val nearestObstacleDepth: Float
)