package com.example.visionfusion.facedetection

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.visionfusion.R
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import kotlin.math.min

class FaceOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: FaceDetectorResult? = null
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var scaleFactor = 1f
    private var bounds = Rect()

    init {
        initPaints()
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

    fun clear() {
        results = null
        initPaints()
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.detections()?.forEach { detection ->
            val box = detection.boundingBox()
            val left = box.left * scaleFactor
            val top = box.top * scaleFactor
            val right = box.right * scaleFactor
            val bottom = box.bottom * scaleFactor

            val rectF = RectF(left, top, right, bottom)
            canvas.drawRect(rectF, boxPaint)

            val category = detection.categories()[0]
            val scoreTxt = "Face %.2f".format(category.score())

            textBackgroundPaint.getTextBounds(scoreTxt, 0, scoreTxt.length, bounds)
            canvas.drawRect(
                left,
                top,
                left + bounds.width() + 8,
                top + bounds.height() + 8,
                textBackgroundPaint
            )
            canvas.drawText(scoreTxt, left, top + bounds.height(), textPaint)
        }
    }

    fun setResults(
        detectionResults: FaceDetectorResult,
        imageHeight: Int,
        imageWidth: Int
    ) {
        results = detectionResults
        scaleFactor = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        invalidate()
    }
}
