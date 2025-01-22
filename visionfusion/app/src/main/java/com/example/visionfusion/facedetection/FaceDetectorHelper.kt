package com.example.visionfusion.facedetection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult

class FaceDetectorHelper(
    val context: Context,
    var threshold: Float = THRESHOLD_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val faceDetectorListener: DetectorListener? = null
) {

    private var faceDetector: FaceDetector? = null

    init {
        setupFaceDetector()
    }

    fun clearFaceDetector() {
        faceDetector?.close()
        faceDetector = null
    }

    fun setupFaceDetector() {
        val baseOptionsBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
        }

        // Asegúrate de que existe el archivo en assets/face_detection/face_detection_short_range.tflite
        baseOptionsBuilder.setModelAssetPath("face_detection/face_detection_short_range.tflite")

        val optionsBuilder = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinDetectionConfidence(threshold)
            .setRunningMode(runningMode)

        if (runningMode == RunningMode.LIVE_STREAM) {
            optionsBuilder
                .setResultListener(::returnLivestreamResult)
                .setErrorListener(::returnLivestreamError)
        }

        try {
            faceDetector = FaceDetector.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            faceDetectorListener?.onError("Error al inicializar FaceDetector: ${e.message}", GPU_ERROR)
            Log.e(TAG, "Error: ${e.message}")
        }
    }

    fun isClosed() = (faceDetector == null)

    fun detectImage(bitmap: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE)
            throw IllegalArgumentException("RunningMode debe ser IMAGE")

        faceDetector ?: return null
        val startTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result = faceDetector?.detect(mpImage) ?: return null
        val endTime = SystemClock.uptimeMillis()
        return ResultBundle(listOf(result), endTime - startTime, bitmap.height, bitmap.width)
    }

    fun detectLivestreamFrame(imageProxy: ImageProxy) {
        if (runningMode != RunningMode.LIVE_STREAM)
            throw IllegalArgumentException("RunningMode debe ser LIVE_STREAM")

        val frameTime = SystemClock.uptimeMillis()

        val bmp = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        imageProxy.use {
            bmp.copyPixelsFromBuffer(it.planes[0].buffer)
        }

        val rotationDeg = imageProxy.imageInfo.rotationDegrees.toFloat()
        // Ajustamos rotación, y si es la frontal, espejar
        val matrix = Matrix().apply {
            postRotate(rotationDeg)
            // EJEMPLO: espejar horizontal
            // postScale(-1f, 1f, bmp.width/2f, bmp.height/2f)
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bmp, 0, 0, bmp.width, bmp.height, matrix, true
        )
        imageProxy.close()

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        faceDetector?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(result: FaceDetectorResult, input: MPImage) {
        val inferenceTime = SystemClock.uptimeMillis() - result.timestampMs()
        faceDetectorListener?.onResults(
            ResultBundle(listOf(result), inferenceTime, input.height, input.width)
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        faceDetectorListener?.onError(error.message ?: "Error desconocido", OTHER_ERROR)
    }

    fun detectVideoFile(uri: Uri, intervalMs: Long): ResultBundle? {
        if (runningMode != RunningMode.VIDEO)
            throw IllegalArgumentException("RunningMode debe ser VIDEO")

        faceDetector ?: return null

        val start = SystemClock.uptimeMillis()
        var errorOccurred = false

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)

        val videoLenMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height
        if (videoLenMs == null || width == null || height == null) return null

        val resultsList = mutableListOf<FaceDetectorResult>()
        val frameCount = videoLenMs / intervalMs

        for (i in 0..frameCount) {
            val tsMs = i * intervalMs
            val frame = retriever.getFrameAtTime(tsMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            frame?.let {
                val argbFrame = if (it.config == Bitmap.Config.ARGB_8888) it
                else it.copy(Bitmap.Config.ARGB_8888, false)

                val mpImage = BitmapImageBuilder(argbFrame).build()
                val result = faceDetector?.detectForVideo(mpImage, tsMs)
                if (result != null) {
                    resultsList.add(result)
                } else {
                    errorOccurred = true
                }
            } ?: run { errorOccurred = true }
        }
        retriever.release()

        val inferenceTime = (SystemClock.uptimeMillis() - start) / (frameCount + 1)
        return if (errorOccurred) null
        else ResultBundle(resultsList, inferenceTime, height, width)
    }

    data class ResultBundle(
        val results: List<FaceDetectorResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val THRESHOLD_DEFAULT = 0.5f
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val TAG = "FaceDetectorHelper"
    }

    interface DetectorListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
