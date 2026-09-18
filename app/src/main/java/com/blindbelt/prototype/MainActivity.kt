package com.blindbelt.prototype

import android.Manifest
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraProvider
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private var mjpegStreamReader: MjpegStreamReader? = null
    private val inferenceScope = CoroutineScope(Dispatchers.Default)
    private var inferenceJob: Job? = null
    // Dedicated single-thread executor: camera frames + NCNN inference run off the UI thread,
    // serialized so the (non-thread-safe) net is never hit by two frames at once.
    private val inferenceExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private lateinit var frameProcessor: FrameProcessor
    private lateinit var imuTracker: ImuTracker
    private lateinit var hapticFeedbackManager: HapticFeedbackManager
    private lateinit var audioAlertManager: AudioAlertManager
    private lateinit var statusText: TextView
    private lateinit var previewImageView: ImageView
    private lateinit var depthOverlayView: DepthOverlayView
    private lateinit var beltSim: BeltSimulatorView
    private val yoloDepthEstimator by lazy { YoloDepthEstimator(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize UI elements
        statusText = findViewById(R.id.statusText)
        previewImageView = findViewById(R.id.previewImageView)
        depthOverlayView = findViewById(R.id.depthOverlayView)
        beltSim = findViewById(R.id.beltSimulatorView)
        
        // Initialize haptic feedback manager
        hapticFeedbackManager = HapticFeedbackManager(this)
        
        // Initialize audio alert manager
        audioAlertManager = AudioAlertManager()
        
        // Initialize the depth estimator on a background thread (NCNN load + backend
        // benchmark take ~2s; the frame processor returns empty frames until it's ready).
        Thread { yoloDepthEstimator.loadModel() }.start()
        frameProcessor = FrameProcessor(this, yoloDepthEstimator)
        frameProcessor.setHapticFeedbackManager(hapticFeedbackManager)
        frameProcessor.setAudioAlertManager(audioAlertManager)
        
        // Set up status update callback
        frameProcessor.onStatusUpdate = { status ->
            runOnUiThread {
                statusText.text = status
            }
        }
        
        // Set up decision update callback
        frameProcessor.onDecisionUpdate = { decision, zones ->
            runOnUiThread {
                depthOverlayView.updateDecision(decision, zones)
            }
        }

        // Set up belt simulator update callback (discs mirror the v2 command sent to the belt)
        frameProcessor.onBeltUpdate = { cmd, band ->
            runOnUiThread {
                beltSim.update(cmd, band)
            }
        }
        
        // Set up depth update callback
        frameProcessor.onDepthUpdate = { depthFrame ->
            runOnUiThread {
                depthOverlayView.updateDepth(depthFrame)
            }
        }
        
        // Initialize IMU tracker
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        imuTracker = ImuTracker(sensorManager)
        imuTracker.startTracking()
        
        if (BeltConfig.USE_MJPEG_SOURCE) {
            // Streaming mode: pull frames from the ESP32 MJPEG stream, skip CameraX entirely
            Log.d("BeltSteering", "Video source: MJPEG ${BeltConfig.STREAM_URL}")
            val reader = MjpegStreamReader { bitmap ->
                runOnUiThread {
                    previewImageView.setImageBitmap(bitmap)
                }
            }
            mjpegStreamReader = reader
            reader.start()
            inferenceJob = inferenceScope.launch {
                while (isActive) {
                    val bitmap = reader.takeLatestFrame()
                    if (bitmap != null) {
                        frameProcessor.processFrame(bitmap)
                    } else {
                        delay(50)
                    }
                }
            }
        } else {
            Log.d("BeltSteering", "Video source: NATIVE")
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                // Request permissions
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mjpegStreamReader?.stop()
        inferenceJob?.cancel()
        inferenceScope.cancel()
        inferenceExecutor.shutdown()
        imuTracker.stopTracking()
        audioAlertManager.release()
    }
    
    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Create preview use case
                val preview = Preview.Builder().build()
                
                // Get the preview view from layout
                val previewView = findViewById<PreviewView>(R.id.previewView)
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                // Use ImageAnalysis for frame processing - FPS measurement
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                
                imageAnalysis.setAnalyzer(inferenceExecutor) { imageProxy: ImageProxy ->
                    // Runs on the dedicated inference thread, never the UI thread.
                    frameProcessor.processFrame(imageProxy)
                }
                
                // Select back camera
                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                
                try {
                    // Unbind all use cases
                    cameraProvider.unbindAll()
                    
                    // Bind to lifecycle
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis
                    )
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                }
                
            } catch (exc: ExecutionException) {
                Log.e(TAG, "Camera initialization failed", exc)
            } catch (exc: InterruptedException) {
                Log.e(TAG, "Camera initialization interrupted", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }
}