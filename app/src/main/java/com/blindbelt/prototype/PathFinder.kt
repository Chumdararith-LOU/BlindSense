package com.blindbelt.prototype

class PathFinder {
    companion object {
        private const val PASSABLE_ZONE_M = 1.5f
    }

    fun decide(
        obstacleResult: ObstacleResult,
        dropOffResult: DropOffResult,
        zoneDepth: ZoneDepth
    ): PathDecision {
        val hasDropOff = dropOffResult.dropOffCount > 0
        val hasObstacle = obstacleResult.obstacleCount > 0

        return when {
            hasDropOff -> PathDecision(
                direction = Direction.STOP,
                dangerLevel = DangerLevel.HIGH,
                nearestDangerDepth = dropOffResult.maxDropOffDepth
            )
            hasObstacle -> {
                // No passable corridor: every zone is a close wall => STOP, don't steer into it.
                val best = maxOf(zoneDepth.leftAvg, zoneDepth.centerAvg, zoneDepth.rightAvg)
                if (best < PASSABLE_ZONE_M) {
                    PathDecision(
                        direction = Direction.STOP,
                        dangerLevel = DangerLevel.HIGH,
                        nearestDangerDepth = obstacleResult.nearestObstacleDepth
                    )
                } else {
                    // Steer toward the zone with the largest (clearest) average depth.
                    val direction = when {
                        zoneDepth.leftAvg >= zoneDepth.centerAvg && zoneDepth.leftAvg >= zoneDepth.rightAvg -> Direction.LEFT
                        zoneDepth.rightAvg >= zoneDepth.centerAvg && zoneDepth.rightAvg >= zoneDepth.leftAvg -> Direction.RIGHT
                        else -> Direction.CENTER
                    }
                    PathDecision(
                        direction = direction,
                        dangerLevel = DangerLevel.MEDIUM,
                        nearestDangerDepth = obstacleResult.nearestObstacleDepth
                    )
                }
            }
            else -> PathDecision(
                direction = Direction.CENTER,
                dangerLevel = DangerLevel.LOW,
                nearestDangerDepth = Float.MAX_VALUE
            )
        }
    }
}
