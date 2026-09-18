package com.blindbelt.prototype

import android.graphics.Bitmap

class FakeDepthEstimator(
    private val simulateObstacle: Boolean = false,
    private val simulateDropOff: Boolean = false
) : DepthEstimator {
    override fun estimate(bitmap: Bitmap, timestampMs: Long): DepthFrame {
        // Create depth array with vertical gradient (bottom close, top far)
        val width = bitmap.width
        val height = bitmap.height
        val depth = FloatArray(width * height) { index ->
            // Calculate row number from index
            val row = index / width
            // Map row to depth value: bottom rows near (1.0), top rows far (10.0)
            1.0f + (row.toFloat() / height.toFloat()) * 9.0f
        }
        
        // Add simulated drop-off if enabled
        if (simulateDropOff) {
            val dropOffRows = (height * 0.15f).toInt()
            
            // Set the bottom 15% of image rows to depth 20.0 meters
            for (row in (height - dropOffRows) until height) {
                for (col in 0 until width) {
                    val index = row * width + col
                    depth[index] = 20.0f
                }
            }
        }
        
        // Add simulated obstacle if enabled
        if (simulateObstacle) {
            val startRow = (height * 0.4f).toInt()
            val endRow = (height * 0.7f).toInt()
            val startCol = (width * 0.65f).toInt()
            val endCol = (width * 0.95f).toInt()
            
            // Set all pixels inside the obstacle region to 1.2 meters
            for (row in startRow until endRow) {
                for (col in startCol until endCol) {
                    val index = row * width + col
                    depth[index] = 1.2f
                }
            }
        }
        
        return DepthFrame(width, height, timestampMs, depth)
    }
}