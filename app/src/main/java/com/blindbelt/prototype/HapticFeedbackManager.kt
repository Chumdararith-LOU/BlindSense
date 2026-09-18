package com.blindbelt.prototype

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

class HapticFeedbackManager(context: Context) {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var lastVibrationTime = 0L

    fun notifyDecision(pathDecision: PathDecision) {
        val now = System.currentTimeMillis()
        
        if (pathDecision.direction == Direction.CENTER) {
            vibrator.cancel()
            return
        }

        if (now - lastVibrationTime < 1200L) return
        lastVibrationTime = now

        Log.d("Haptics", "VIBRATING. Dir: ${pathDecision.direction}, Danger: ${pathDecision.dangerLevel}")

        val effect = when (pathDecision.direction) {
            Direction.STOP -> VibrationEffect.createOneShot(1000, 255)
            Direction.LEFT -> VibrationEffect.createOneShot(800, 255)
            Direction.RIGHT -> VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), intArrayOf(255, 255, 255, 255), -1)
            Direction.CENTER -> VibrationEffect.createOneShot(500, 255)
        }
        vibrator.vibrate(effect)
    }
}