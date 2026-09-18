package com.blindbelt.prototype

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import java.nio.FloatBuffer

class YoloDepthEstimator(private val context: Context) : DepthEstimator {
    private val INPUT_SIZE = 480
    private val ortEnv = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private var isModelLoaded = false

    private val inputPixels = IntArray(INPUT_SIZE * INPUT_SIZE)
    private val inputFloatArray = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
    private val outputDepthArray = FloatArray(INPUT_SIZE * INPUT_SIZE)
    private val inputBuffer = FloatBuffer.wrap(inputFloatArray)
    private val scaledBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
    private val scaleCanvas = Canvas(scaledBitmap)
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    @Synchronized
    fun loadModel() {
        val bytes = try {
            context.assets.open("yolo26n-depth.onnx").readBytes()
        } catch (e: Exception) {
            Log.e("YoloDepth", "Model asset missing: ${e.message}")
            return
        }

        val options = OrtSession.SessionOptions()
        try {
            ortSession = ortEnv.createSession(bytes, options)
            isModelLoaded = true
            Log.d("YoloDepth", "Inputs: ${ortSession!!.inputNames} Outputs: ${ortSession!!.outputNames}")
        } catch (e: Exception) {
            Log.e("YoloDepth", "Session creation failed: ${e.message}")
        }
    }

    @Synchronized
    override fun estimate(bitmap: Bitmap, timestampMs: Long): DepthFrame {
        if (!isModelLoaded) {
            return DepthFrame(INPUT_SIZE, INPUT_SIZE, timestampMs, FloatArray(INPUT_SIZE * INPUT_SIZE))
        }

        try {
            // 1. Scale bitmap to INPUT_SIZE square, getPixels into reusable IntArray
            scaleCanvas.drawBitmap(bitmap, null, Rect(0, 0, INPUT_SIZE, INPUT_SIZE), scalePaint)
            scaledBitmap.getPixels(inputPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            // 2. Fill reusable FloatArray NCHW, /255f
            for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
                val pixel = inputPixels[i]
                inputFloatArray[i] = ((pixel shr 16 and 0xFF) / 255.0f)
                inputFloatArray[i + INPUT_SIZE * INPUT_SIZE] = ((pixel shr 8 and 0xFF) / 255.0f)
                inputFloatArray[i + 2 * INPUT_SIZE * INPUT_SIZE] = ((pixel and 0xFF) / 255.0f)
            }

            // 3. Run session with shape [1,3,480,480]
            val inputName = ortSession!!.inputNames.iterator().next()
            val shape = longArrayOf(1L, 3L, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            inputBuffer.rewind()
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, shape)
            inputTensor.use { tensor ->
                val results = ortSession?.run(mapOf(inputName to tensor))
                results?.use { res ->
                    when (val output = res.get(0)) {
                        is OnnxTensor -> output.floatBuffer.get(outputDepthArray)
                        is Array<*> -> {
                            var idx = 0
                            fun flatten(a: Any?) {
                                when (a) {
                                    is Array<*> -> a.forEach { flatten(it) }
                                    is Number -> if (idx < outputDepthArray.size) outputDepthArray[idx++] = a.toFloat()
                                }
                            }
                            flatten(output)
                        }
                        else -> Log.w("YoloDepth", "Unexpected output type: ${output?.javaClass}")
                    }
                }
            }

            val minVal = outputDepthArray.minOrNull() ?: 0f
            val maxVal = outputDepthArray.maxOrNull() ?: 0f
            Log.d("YoloDepth", "Real AI Output -> Min: $minVal, Max: $maxVal, First 5: ${outputDepthArray.take(5).toList()}")

            return DepthFrame(INPUT_SIZE, INPUT_SIZE, timestampMs, outputDepthArray)
        } catch (e: Exception) {
            Log.e("YoloDepth", "estimate() failed: ${e.message}")
            return DepthFrame(INPUT_SIZE, INPUT_SIZE, timestampMs, FloatArray(INPUT_SIZE * INPUT_SIZE))
        }
    }
}
