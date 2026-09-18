package com.blindbelt.prototype

class ZoneDepthAnalyzer {
    fun analyze(depthFrame: DepthFrame): ZoneDepth {
        val width = depthFrame.width
        val height = depthFrame.height
        
        // Define zones based on width
        val leftZoneEnd = (width * 0.33f).toInt()
        val centerZoneEnd = (width * 0.66f).toInt()
        
        // Define vertical range (40% to 80% of image height)
        val minHeight = (height * 0.4f).toInt()
        val maxHeight = (height * 0.8f).toInt()
        
        // Calculate averages for each zone
        val leftAvg = calculateZoneAverage(depthFrame, 0, leftZoneEnd, minHeight, maxHeight)
        val centerAvg = calculateZoneAverage(depthFrame, leftZoneEnd, centerZoneEnd, minHeight, maxHeight)
        val rightAvg = calculateZoneAverage(depthFrame, centerZoneEnd, width, minHeight, maxHeight)
        
        return ZoneDepth(leftAvg, centerAvg, rightAvg)
    }
    
    private fun calculateZoneAverage(
        depthFrame: DepthFrame,
        startX: Int,
        endX: Int,
        startY: Int,
        endY: Int
    ): Float {
        var sum = 0.0f
        var count = 0
        
        // Iterate through the specified region
        for (y in startY until endY) {
            for (x in startX until endX) {
                val depthIndex = y * depthFrame.width + x
                val depth = depthFrame.depth[depthIndex]
                
                // Only consider finite depth values
                if (depth.isFinite()) {
                    sum += depth
                    count++
                }
            }
        }
        
        // Return Float.MAX_VALUE if no valid pixels found, otherwise return average
        return if (count == 0) Float.MAX_VALUE else sum / count
    }
}