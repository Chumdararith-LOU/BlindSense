package com.blindbelt.prototype

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

class MjpegStreamReader(
    private val onFrame: (Bitmap) -> Unit
) {
    private val TAG = "MjpegStreamReader"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private val latestFrame = AtomicReference<Bitmap?>(null)

    fun takeLatestFrame(): Bitmap? = latestFrame.getAndSet(null)

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    readStream()
                } catch (e: Exception) {
                    Log.e(TAG, "Stream error, retrying in 2s", e)
                    delay(2000)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // ponytail: blocking read() is only interrupted by readTimeout (15s), so stop()
    // releases the socket within one timeout window. Upgrade with runInterruptible if teardown latency matters.
    private fun readStream() {
        val connection = (URL(BeltConfig.STREAM_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 15000
        }
        try {
            val input = connection.inputStream
            val buffer = ByteArray(64 * 1024)
            val frame = ByteArrayOutputStream()
            var inFrame = false
            var prev = 0
            var frameCount = 0
            var lastFpsLogTime = System.currentTimeMillis()
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                for (i in 0 until read) {
                    val b = buffer[i].toInt() and 0xFF
                    if (inFrame) {
                        frame.write(b)
                        if (prev == 0xFF && b == 0xD9) {
                            val jpeg = frame.toByteArray()
                            frame.reset()
                            inFrame = false
                            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                            if (bitmap != null) {
                                latestFrame.set(bitmap)
                                onFrame(bitmap)
                                frameCount++
                                val now = System.currentTimeMillis()
                                if (now - lastFpsLogTime >= 1000) {
                                    Log.d("BeltSteering", "MJPEG FPS: $frameCount")
                                    frameCount = 0
                                    lastFpsLogTime = now
                                }
                            }
                        }
                    } else if (prev == 0xFF && b == 0xD8) {
                        frame.write(0xFF)
                        frame.write(0xD8)
                        inFrame = true
                    }
                    prev = b
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
