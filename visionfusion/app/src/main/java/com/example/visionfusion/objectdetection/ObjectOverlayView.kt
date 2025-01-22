package com.example.visionfusion.objectdetection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.visionfusion.R
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import kotlin.math.max
import kotlin.math.min

class ObjectOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: ObjectDetectorResult? = null
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var scaleFactor = 1f
    private var bounds = Rect()

    private var outputWidth = 0
    private var outputHeight = 0
    private var outputRotate = 0

    // Por defecto, IMAGE
    private var runningMode: RunningMode = RunningMode.IMAGE

    init {
        initPaints()
    }

    fun clear() {
        results = null
        initPaints()
        invalidate()
    }

    fun setRunningMode(mode: RunningMode) {
        runningMode = mode
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 40f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 40f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.mp_primary)
        boxPaint.strokeWidth = 6f
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val detectionResult = results ?: return

        for (detection in detectionResult.detections()) {
            val box = detection.boundingBox()
            val rectF = RectF(box.left, box.top, box.right, box.bottom)

            // Rotamos y trasladamos
            val matrix = Matrix()
            matrix.postTranslate(-outputWidth / 2f, -outputHeight / 2f)
            matrix.postRotate(outputRotate.toFloat())
            if (outputRotate == 90 || outputRotate == 270) {
                matrix.postTranslate(outputHeight / 2f, outputWidth / 2f)
            } else {
                matrix.postTranslate(outputWidth / 2f, outputHeight / 2f)
            }
            matrix.mapRect(rectF)

            // Escalado final
            val left = rectF.left * scaleFactor
            val top = rectF.top * scaleFactor
            val right = rectF.right * scaleFactor
            val bottom = rectF.bottom * scaleFactor

            // Dibujar bounding box
            val drawableRect = RectF(left, top, right, bottom)
            canvas.drawRect(drawableRect, boxPaint)

            // Texto
            val mainCat = detection.categories()[0]
            val text = "${mainCat.categoryName()} ${"%.2f".format(mainCat.score())}"

            textBackgroundPaint.getTextBounds(text, 0, text.length, bounds)
            canvas.drawRect(
                left,
                top,
                left + bounds.width() + 8,
                top + bounds.height() + 8,
                textBackgroundPaint
            )
            canvas.drawText(text, left, top + bounds.height(), textPaint)
        }
    }

    fun setResults(
        detectionResults: ObjectDetectorResult,
        imageHeight: Int,
        imageWidth: Int,
        imageRotation: Int
    ) {
        results = detectionResults
        outputWidth = imageWidth
        outputHeight = imageHeight
        outputRotate = imageRotation

        // Ajustamos factor de escala
        val (rotW, rotH) = when (imageRotation) {
            90, 270 -> Pair(imageHeight, imageWidth)
            else -> Pair(imageWidth, imageHeight)
        }
        scaleFactor = when (runningMode) {
            RunningMode.IMAGE, RunningMode.VIDEO -> {
                min(width.toFloat() / rotW, height.toFloat() / rotH)
            }
            RunningMode.LIVE_STREAM -> {
                max(width.toFloat() / rotW, height.toFloat() / rotH)
            }
        }

        invalidate()
    }
}
