package com.blindbelt.prototype

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

// Extension function to convert ImageProxy to Bitmap
fun ImageProxy.toBitmap(): Bitmap {
    // Use the built-in toBitmap() function if available (CameraX 1.3.0+)
    try {
        return this@toBitmap.toBitmap()
    } catch (e: NoSuchMethodError) {
        // Fallback for older CameraX versions
        val image = this@toBitmap
        if (image.format == ImageFormat.YUV_420_888) {
            // Convert YUV_420_888 to Bitmap
            val yuvData = yuv420888ToNv21(image)
            val yuvImage = YuvImage(yuvData, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } else {
            // For other image formats, try direct conversion
            return this@toBitmap.toBitmap()
        }
    }
}

// Helper function to convert YUV_420_888 to NV21 format
private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val planes = image.planes
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    
    val nv21 = ByteArray(ySize + uSize + vSize)
    
    // Copy Y plane
    yBuffer.get(nv21, 0, ySize)
    
    // Copy V and U planes (interleaved in NV21 format)
    val vBase = ySize
    val uBase = ySize + vSize
    vBuffer.get(nv21, vBase, vSize)
    uBuffer.get(nv21, uBase, uSize)
    
    return nv21
}

class FrameProcessor(private val context: Context, private val depthEstimator: DepthEstimator) {
    private val TAG = "FrameProcessor"
    private val frameCount = AtomicLong(0)
    private val lastFpsLogTime = AtomicLong(0)
    private val lastStatusLogTime = AtomicLong(0)
    private var currentFps = 0.0
    private val groundPlaneEstimator = GroundPlaneEstimator()
    private val obstacleDetector = ObstacleDetector()
    private val dropOffDetector = DropOffDetector()
    private val zoneDepthAnalyzer = ZoneDepthAnalyzer()
    private val pathFinder = PathFinder()
    private var hapticFeedbackManager: HapticFeedbackManager? = null
    private var audioAlertManager: AudioAlertManager? = null
    private var lastVibrationTime = 0L
    private val VIBRATION_COOLDOWN_MS = 1000L
    private val udpSender = UdpHapticSender()
    private var lastCmd = -1
    private var lastBand = -1
    private var lastSendTime = 0L
    var onStatusUpdate: ((String) -> Unit)? = null
    var onDepthUpdate: ((DepthFrame) -> Unit)? = null
    var onDecisionUpdate: ((PathDecision, ZoneDepth) -> Unit)? = null
    var onBeltUpdate: ((Int, Int) -> Unit)? = null
    
    init {
        // FrameProcessor should not be responsible for loading the model
    }
    
    fun setHapticFeedbackManager(hapticFeedbackManager: HapticFeedbackManager) {
        this.hapticFeedbackManager = hapticFeedbackManager
    }
    
    fun setAudioAlertManager(audioAlertManager: AudioAlertManager) {
        this.audioAlertManager = audioAlertManager
    }
    
    fun processFrame(imageProxy: ImageProxy) {
        // Convert ImageProxy to Bitmap
        val bitmap = imageProxy.toBitmap()
        
        // Get rotation degrees from the image proxy
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        
        // Rotate bitmap if needed to match display orientation
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle() // Recycle the original bitmap to prevent memory leaks
            rotated
        } else {
            bitmap
        }
        
        Log.d(TAG, "Rotation: degrees=$rotationDegrees")
        
        processFrame(rotatedBitmap)
        
        // Close the image proxy to free up resources
        imageProxy.close()
    }
    
    fun processFrame(bitmap: Bitmap) {
        // Increment frame counter
        frameCount.incrementAndGet()
        
        // Get the current time
        val currentTime = System.currentTimeMillis()
        
        // Log FPS once per second (1000 milliseconds)
        val fpsWindowStart = lastFpsLogTime.get()
        if (currentTime - fpsWindowStart >= 1000) {
            if (lastFpsLogTime.compareAndSet(fpsWindowStart, currentTime)) {
                val currentFrame = frameCount.getAndSet(0)
                val fps = currentFrame * 1000.0 / (currentTime - fpsWindowStart)
                currentFps = fps
                Log.d(TAG, "FPS: $fps")
            }
        }
        
        // Estimate depth for this frame using our custom estimator
        val depthFrame = depthEstimator.estimate(bitmap, currentTime)
        
        // Notify any listeners of the depth update
        onDepthUpdate?.invoke(depthFrame)
        
        // Calculate average depth and log once per second
        val avgDepth = depthFrame.depth.sum() / depthFrame.depth.size
        Log.d(TAG, "Depth: width=${depthFrame.width}, height=${depthFrame.height}, avg=$avgDepth")
        
        // Log warning once per second if depth frame is all zeros
        val isAllZeros = depthFrame.depth.all { it == 0.0f }
        if (isAllZeros) {
            android.util.Log.w("YoloDepth", "WARNING: depth frame is all zeros")
        }
        
        // Estimate ground plane
        val groundPlane = groundPlaneEstimator.estimate(depthFrame, 0.0f) // pitchDegrees not used yet
        
        // Count ground rows and calculate min/max depths for logging
        val groundRows = groundPlane.isGroundRow.count { it }
        val bottomDepth = groundPlane.rowMedianDepth.filter { it > 0.0f }.minOrNull() ?: 0.0f
        val topDepth = groundPlane.rowMedianDepth.maxOrNull() ?: 0.0f
        
        Log.d(TAG, "Ground: groundRows=$groundRows, bottomDepth=$bottomDepth, topDepth=$topDepth")
        
        // Detect obstacles
        val obstacleResult = obstacleDetector.detect(depthFrame, groundPlane)
        
        // Log obstacle information once per second
        Log.d(TAG, "Obstacles: count=${obstacleResult.obstacleCount}, nearestDepth=${obstacleResult.nearestObstacleDepth}")
        
        // Detect drop-offs
        val dropOffResult = dropOffDetector.detect(depthFrame, groundPlane)
        
        // Log drop-off information once per second
        Log.d(TAG, "DropOff: count=${dropOffResult.dropOffCount}, maxDepth=${dropOffResult.maxDropOffDepth}")
        
        // Analyze zone depths
        val zoneDepth = zoneDepthAnalyzer.analyze(depthFrame)
        
        // Log zone depth information once per second
        Log.d(TAG, "Zones: left=${zoneDepth.leftAvg}, center=${zoneDepth.centerAvg}, right=${zoneDepth.rightAvg}")
        
        // Make path decision
        val pathDecision = pathFinder.decide(obstacleResult, dropOffResult, zoneDepth)
        
        // Log path decision information once per second
        Log.d(TAG, "Path: direction=${pathDecision.direction}, danger=${pathDecision.dangerLevel}, nearestDepth=${pathDecision.nearestDangerDepth}")
        
        // Trigger haptic feedback on every frame (instead of only once per second)
        hapticFeedbackManager?.notifyDecision(pathDecision)
        
        // Send v2 belt command derived from the path decision (UDP send runs on a background coroutine)
        analyzeDepthAndSteer(pathDecision, dropOffResult.dropOffCount)
        
        // Call decision update callback
        onDecisionUpdate?.invoke(pathDecision, zoneDepth)
        
        // Provide haptic feedback and audio alert once per second (not on every frame)
        val statusLogTime = lastStatusLogTime.get()
        if (currentTime - statusLogTime >= 1000) {
            if (lastStatusLogTime.compareAndSet(statusLogTime, currentTime)) {
                // Log that 1s tick is firing for haptic feedback
                android.util.Log.d("FrameProcessor", "1s TICK. Calling Audio.")
                audioAlertManager?.notifyDecision(pathDecision)
                
                // Build status string and update UI
                val status = buildStatusString(
                    fps = currentFps,
                    obstacleCount = obstacleResult.obstacleCount,
                    dropOffCount = dropOffResult.dropOffCount,
                    direction = pathDecision.direction,
                    dangerLevel = pathDecision.dangerLevel
                )
                onStatusUpdate?.invoke(status)
            }
        }
        
    }
    
    fun processDepthMap(depthArray: FloatArray): PathDecision {
        val width = 640
        var leftSum = 0f
        var centerSum = 0f
        var rightSum = 0f
        var leftCount = 0
        var centerCount = 0
        var rightCount = 0

        for (y in 192..639) {
            val rowBase = y * width
            for (x in 0..639) {
                val depth = depthArray[rowBase + x]
                when {
                    x in 0..212 -> { leftSum += depth; leftCount++ }
                    x in 213..426 -> { centerSum += depth; centerCount++ }
                    else -> { rightSum += depth; rightCount++ }
                }
            }
        }

        val leftAvg = if (leftCount > 0) leftSum / leftCount else Float.MAX_VALUE
        val centerAvg = if (centerCount > 0) centerSum / centerCount else Float.MAX_VALUE
        val rightAvg = if (rightCount > 0) rightSum / rightCount else Float.MAX_VALUE

        val decision = when {
            centerAvg < 2.0f -> PathDecision(Direction.STOP, DangerLevel.HIGH, centerAvg)
            rightAvg < 2.0f -> PathDecision(Direction.LEFT, DangerLevel.LOW, rightAvg)
            leftAvg < 2.0f -> PathDecision(Direction.RIGHT, DangerLevel.LOW, leftAvg)
            else -> PathDecision(Direction.CENTER, DangerLevel.LOW, Float.MAX_VALUE)
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVibrationTime > VIBRATION_COOLDOWN_MS) {
            hapticFeedbackManager?.notifyDecision(decision)
            lastVibrationTime = currentTime
        }

        return decision
    }

    fun analyzeDepthAndSteer(decision: PathDecision, dropOffCount: Int) {
        val (cmd, band) = udpSender.commandFor(decision.direction, dropOffCount > 0, decision.nearestDangerDepth)

        val now = System.currentTimeMillis()
        val valuesChanged = cmd != lastCmd || band != lastBand
        if (valuesChanged || now - lastSendTime >= 200L) {
            udpSender.sendV2(cmd, band)
            onBeltUpdate?.invoke(cmd, band)
            lastCmd = cmd
            lastBand = band
            lastSendTime = now
        }
    }

    private fun buildStatusString(
        fps: Double,
        obstacleCount: Int,
        dropOffCount: Int,
        direction: Direction,
        dangerLevel: DangerLevel
    ): String {
        return buildString {
            append("FPS: ${"%.1f".format(fps)}\n")
            append("Obstacles: $obstacleCount\n")
            append("Drop-offs: $dropOffCount\n")
            append("Direction: $direction\n")
            append("Danger: $dangerLevel")
        }
    }
}