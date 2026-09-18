package com.blindbelt.prototype

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class DepthOverlayView : View {
    private var depthFrame: DepthFrame? = null
    private var pathDecision: PathDecision? = null
    private var zoneDepth: ZoneDepth? = null
    private val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val paint = Paint()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val zonePaint = Paint()
    private val arrowPaint = Paint()
    private val textPaint = Paint()
    private val borderPaint = Paint()

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        bitmapPaint.alpha = 115 // ~0.45 alpha
        
        zonePaint.isAntiAlias = true
        zonePaint.style = Paint.Style.FILL
        
        arrowPaint.isAntiAlias = true
        arrowPaint.color = Color.GREEN
        arrowPaint.strokeWidth = 8f
        arrowPaint.strokeCap = Paint.Cap.ROUND
        arrowPaint.strokeJoin = Paint.Join.ROUND
        
        textPaint.isAntiAlias = true
        textPaint.color = Color.RED
        textPaint.textSize = 48f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        
        borderPaint.isAntiAlias = true
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = 8f
    }

    fun updateDepth(depthFrame: DepthFrame) {
        this.depthFrame = depthFrame
        rebuildBitmap()
        invalidate()
    }

    fun updateDecision(pathDecision: PathDecision, zoneDepth: ZoneDepth) {
        this.pathDecision = pathDecision
        this.zoneDepth = zoneDepth
        invalidate()
    }

    private fun rebuildBitmap() {
        val frame = depthFrame ?: return
        val width = frame.width
        val height = frame.height
        val depthData = frame.depth

        // Downsample to 64x64
        val downsampled = FloatArray(64 * 64)
        for (y in 0..63) {
            for (x in 0..63) {
                val srcX = (x * width / 64).coerceAtMost(width - 1)
                val srcY = (y * height / 64).coerceAtMost(height - 1)
                downsampled[y * 64 + x] = depthData[srcY * width + srcX]
            }
        }

        // Convert to ARGB colors
        for (y in 0..63) {
            for (x in 0..63) {
                val depth = downsampled[y * 64 + x]
                val color = if (depth.isInfinite() || depth.isNaN() || depth <= 0f) {
                    Color.TRANSPARENT // Fully transparent for invalid depth values
                } else {
                    // Clamp depth between 0.5 and 8.0 meters
                    val clampedDepth = max(0.5f, min(8.0f, depth))
                    // Normalize depth to [0,1] range
                    val normalized = (clampedDepth - 0.5f) / (8.0f - 0.5f)
                    // Blend colors: red (0) -> yellow (1/3), green (2/3), blue (1)
                    createColorFromDepth(normalized)
                }
                bitmap.setPixel(x, y, color)
            }
        }
    }

    private fun createColorFromDepth(normalizedDepth: Float): Int {
        // Based on the color blending from red (0) to yellow (1/3), green (2/3), blue (1)
        val r: Float
        val g: Float
        val b: Float

        when {
            normalizedDepth < 1.0f / 3.0f -> {
                // Red to yellow (0 to 1/3)
                val t = normalizedDepth * 3.0f
                r = 1.0f
                g = t
                b = 0.0f
            }
            normalizedDepth < 2.0f / 3.0f -> {
                // Yellow to green (1/3 to 2/3)
                val t = (normalizedDepth - 1.0f / 3.0f) * 3.0f
                r = 1.0f - t
                g = 1.0f
                b = 0.0f
            }
            else -> {
                // Green to blue (2/3 to 1)
                val t = (normalizedDepth - 2.0f / 3.0f) * 3.0f
                r = 0.0f
                g = 1.0f - t
                b = t
            }
        }

        return Color.rgb(
            (r * 255).toInt(),
            (g * 255).toInt(),
            (b * 255).toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()

        if (bitmap.width > 0 && bitmap.height > 0) {
            val scaleX = width / bitmap.width
            val scaleY = height / bitmap.height
            val matrix = Matrix()
            matrix.setScale(scaleX, scaleY)
            canvas.drawBitmap(bitmap, matrix, bitmapPaint)
            
            // Draw zone indicators
            drawZoneIndicators(canvas, width, height)
            
            // Draw direction indicator
            drawDirectionIndicator(canvas, width, height)
            
            // Draw danger border
            drawDangerBorder(canvas, width, height)
        }
    }

    private fun drawZoneIndicators(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        val zoneDepth = zoneDepth ?: return
        val pathDecision = pathDecision ?: return
        
        // Calculate safe zone (zone with highest average depth)
        val safeZone = when {
            zoneDepth.leftAvg >= zoneDepth.centerAvg && zoneDepth.leftAvg >= zoneDepth.rightAvg -> "left"
            zoneDepth.centerAvg >= zoneDepth.rightAvg -> "center"
            else -> "right"
        }
        
        val safeZoneAvg = when (safeZone) {
            "left" -> zoneDepth.leftAvg
            "center" -> zoneDepth.centerAvg
            "right" -> zoneDepth.rightAvg
            else -> 0f
        }
        
        // Calculate zone widths
        val zoneWidth = viewWidth / 3f
        
        // Draw zone tints
        val zonePaint = Paint()
        zonePaint.isAntiAlias = true
        zonePaint.style = Paint.Style.FILL
        
        // Left zone
        if (safeZone != "left" && zoneDepth.leftAvg < safeZoneAvg / 2) {
            zonePaint.color = Color.argb(128, 255, 0, 0) // Light red tint
            canvas.drawRect(0f, 0f, zoneWidth, viewHeight, zonePaint)
        } else if (safeZone == "left") {
            zonePaint.color = Color.argb(128, 0, 255, 0) // Light green tint
            canvas.drawRect(0f, 0f, zoneWidth, viewHeight, zonePaint)
        }
        
        // Center zone
        if (safeZone != "center" && zoneDepth.centerAvg < safeZoneAvg / 2) {
            zonePaint.color = Color.argb(128, 255, 0, 0) // Light red tint
            canvas.drawRect(zoneWidth, 0f, zoneWidth * 2, viewHeight, zonePaint)
        } else if (safeZone == "center") {
            zonePaint.color = Color.argb(128, 0, 255, 0) // Light green tint
            canvas.drawRect(zoneWidth, 0f, zoneWidth * 2, viewHeight, zonePaint)
        }
        
        // Right zone
        if (safeZone != "right" && zoneDepth.rightAvg < safeZoneAvg / 2) {
            zonePaint.color = Color.argb(128, 255, 0, 0) // Light red tint
            canvas.drawRect(zoneWidth * 2, 0f, viewWidth, viewHeight, zonePaint)
        } else if (safeZone == "right") {
            zonePaint.color = Color.argb(128, 0, 255, 0) // Light green tint
            canvas.drawRect(zoneWidth * 2, 0f, viewWidth, viewHeight, zonePaint)
        }
    }

    private fun drawDirectionIndicator(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        val pathDecision = pathDecision ?: return
        
        val centerX = viewWidth / 2f
        val bottomCenterY = viewHeight - 50f
        
        when (pathDecision.direction) {
            Direction.LEFT -> {
                // Draw left arrow
                canvas.drawLine(centerX, bottomCenterY, centerX - 50f, bottomCenterY, arrowPaint)
                canvas.drawLine(centerX - 50f, bottomCenterY, centerX - 30f, bottomCenterY - 20f, arrowPaint)
                canvas.drawLine(centerX - 50f, bottomCenterY, centerX - 30f, bottomCenterY + 20f, arrowPaint)
            }
            Direction.RIGHT -> {
                // Draw right arrow
                canvas.drawLine(centerX, bottomCenterY, centerX + 50f, bottomCenterY, arrowPaint)
                canvas.drawLine(centerX + 50f, bottomCenterY, centerX + 30f, bottomCenterY - 20f, arrowPaint)
                canvas.drawLine(centerX + 50f, bottomCenterY, centerX + 30f, bottomCenterY + 20f, arrowPaint)
            }
            Direction.CENTER -> {
                // Draw up arrow
                canvas.drawLine(centerX, bottomCenterY, centerX, bottomCenterY - 50f, arrowPaint)
                canvas.drawLine(centerX, bottomCenterY - 50f, centerX - 20f, bottomCenterY - 30f, arrowPaint)
                canvas.drawLine(centerX, bottomCenterY - 50f, centerX + 20f, bottomCenterY - 30f, arrowPaint)
            }
            Direction.STOP -> {
                // Draw STOP text
                canvas.drawText("STOP", centerX, bottomCenterY, textPaint)
            }
        }
    }

    private fun drawDangerBorder(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        val pathDecision = pathDecision ?: return
        
        borderPaint.strokeWidth = 8f
        when (pathDecision.dangerLevel) {
            DangerLevel.LOW -> {
                // No border
            }
            DangerLevel.MEDIUM -> {
                borderPaint.color = Color.YELLOW
                canvas.drawRect(4f, 4f, viewWidth - 4f, viewHeight - 4f, borderPaint)
            }
            DangerLevel.HIGH -> {
                borderPaint.color = Color.RED
                canvas.drawRect(4f, 4f, viewWidth - 4f, viewHeight - 4f, borderPaint)
            }
        }
    }
}