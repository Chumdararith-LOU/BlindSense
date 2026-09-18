package com.blindbelt.prototype

import android.graphics.Bitmap

interface DepthEstimator {
    fun estimate(bitmap: Bitmap, timestampMs: Long): DepthFrame
}