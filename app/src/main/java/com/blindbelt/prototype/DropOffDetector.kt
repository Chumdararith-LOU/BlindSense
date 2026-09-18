package com.blindbelt.prototype

class DropOffDetector {
    fun detect(depthFrame: DepthFrame, groundPlane: GroundPlane): DropOffResult {
        val dropOffRows = BooleanArray(groundPlane.height)
        var dropOffCount = 0
        var maxDropOffDepth = 0.0f
        
        // For each row from 1 to height - 1
        for (y in 1 until groundPlane.height) {
            // Calculate difference between current row and previous row
            val diff = groundPlane.rowMedianDepth[y] - groundPlane.rowMedianDepth[y - 1]
            
            // If difference is greater than 1.0f, mark both rows as drop-off
            if (diff > 1.0f) {
                dropOffRows[y] = true
                dropOffRows[y - 1] = true
                
                // Update drop-off count
                dropOffCount++
                
                // Update max drop-off depth if needed
                val currentDepth = groundPlane.rowMedianDepth[y]
                if (currentDepth > maxDropOffDepth) {
                    maxDropOffDepth = currentDepth
                }
                
                val previousDepth = groundPlane.rowMedianDepth[y - 1]
                if (previousDepth > maxDropOffDepth) {
                    maxDropOffDepth = previousDepth
                }
            }
        }
        
        // If no drop-off rows, set to 0.0f
        if (dropOffCount == 0) {
            maxDropOffDepth = 0.0f
        }
        
        return DropOffResult(
            dropOffRows = dropOffRows,
            dropOffCount = dropOffCount,
            maxDropOffDepth = maxDropOffDepth
        )
    }
}