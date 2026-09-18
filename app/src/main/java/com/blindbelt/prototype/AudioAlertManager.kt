package com.blindbelt.prototype

import android.media.ToneGenerator
import android.media.AudioFormat
import android.media.AudioManager
import android.util.Log

class AudioAlertManager {
    private var toneGenerator: ToneGenerator? = null
    
    init {
        // Initialize the ToneGenerator with the correct audio stream and volume
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            Log.e("AudioAlertManager", "Failed to initialize ToneGenerator", e)
        }
    }
    
    fun notifyDecision(pathDecision: PathDecision) {
        when (pathDecision.dangerLevel) {
            DangerLevel.HIGH -> {
                // Play urgent alert sound for about 300 ms
                playAlertSound(300)
                Log.d("AudioAlertManager", "HIGH danger alert sound (300ms)")
            }
            DangerLevel.MEDIUM, DangerLevel.LOW -> {
                // No sound
                Log.d("AudioAlertManager", "No alert - danger level: ${pathDecision.dangerLevel}")
            }
        }
    }
    
    private fun playAlertSound(durationMs: Int) {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, durationMs)
        } catch (e: Exception) {
            Log.e("AudioAlertManager", "Failed to play alert sound", e)
        }
    }
    
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}