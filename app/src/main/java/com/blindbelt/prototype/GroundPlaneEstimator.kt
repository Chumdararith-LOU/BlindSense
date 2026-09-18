package com.blindbelt.prototype

class GroundPlaneEstimator {
    fun estimate(depthFrame: DepthFrame, pitchDegrees: Float): GroundPlane {
        val rowMedianDepth = FloatArray(depthFrame.height)
        val isGroundRow = BooleanArray(depthFrame.height)
        
        // For each row, calculate median depth
        for (row in 0 until depthFrame.height) {
            // Extract the depth values for this row
            val startIdx = row * depthFrame.width
            val endIdx = startIdx + depthFrame.width
            
            // Get the depth values for this row
            val rowDepths = FloatArray(depthFrame.width)
            for (col in 0 until depthFrame.width) {
                rowDepths[col] = depthFrame.depth[startIdx + col]
            }
            
            // Calculate median
            rowDepths.sort()
            val median: Float
            if (rowDepths.size % 2 == 0) {
                val mid1 = rowDepths[rowDepths.size / 2 - 1]
                val mid2 = rowDepths[rowDepths.size / 2]
                median = (mid1 + mid2) / 2.0f
            } else {
                median = rowDepths[rowDepths.size / 2]
            }
            
            rowMedianDepth[row] = median
            
            // Mark as ground row if median depth is finite and greater than 0.0f
            isGroundRow[row] = median.isFinite() && median > 0.0f
        }
        
        return GroundPlane(
            width = depthFrame.width,
            height = depthFrame.height,
            rowMedianDepth = rowMedianDepth,
            isGroundRow = isGroundRow
        )
    }
}