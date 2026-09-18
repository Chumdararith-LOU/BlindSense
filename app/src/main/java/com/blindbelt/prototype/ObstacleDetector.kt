package com.blindbelt.prototype

import android.util.Log

class ObstacleDetector {

    companion object {
        const val THRESH = 2.0f
        const val MARGIN = 0.5f
        const val MIN_PIX_FRAC = 0.01f
    }

    fun detect(depthFrame: DepthFrame, groundPlane: GroundPlane): ObstacleResult {
        val h = depthFrame.height
        val w = depthFrame.width
        val depth = depthFrame.depth

        val rowRef = FloatArray(h)
        val row = FloatArray(w)
        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) row[x] = depth[base + x]
            row.sort()
            rowRef[y] = percentile75(row, w)
        }

        var obsPixels = 0
        var nearest = Float.MAX_VALUE
        val obstacleRows = BooleanArray(h)
        for (y in 0 until h) {
            val base = y * w
            val limit = rowRef[y] - MARGIN
            var rowHas = false
            for (x in 0 until w) {
                val v = depth[base + x]
                if (v > 0.0f && v < limit && v < THRESH) {
                    obsPixels++
                    rowHas = true
                    if (v < nearest) nearest = v
                }
            }
            obstacleRows[y] = rowHas
        }

        val obstacleCount = if (obsPixels > MIN_PIX_FRAC * h * w) 1 else 0

        Log.d("Obstacle", "obsPixels=$obsPixels need=${(MIN_PIX_FRAC*h*w).toInt()} nearest=$nearest")

        return ObstacleResult(
            obstacleRows = obstacleRows,
            obstacleCount = obstacleCount,
            nearestObstacleDepth = nearest
        )
    }

    private fun percentile75(sorted: FloatArray, n: Int): Float {
        if (n == 1) return sorted[0]
        val idx = 0.75f * (n - 1)
        val lo = idx.toInt()
        val hi = lo + 1
        val frac = idx - lo
        return sorted[lo] + frac * (sorted[hi] - sorted[lo])
    }
}
