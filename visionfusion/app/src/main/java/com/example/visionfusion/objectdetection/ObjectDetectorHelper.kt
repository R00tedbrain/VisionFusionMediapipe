//ObjectDetectorHelper
package com.example.visionfusion.objectdetection

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

class ObjectDetectorHelper(
    val context: Context,
    var threshold: Float = THRESHOLD_DEFAULT,
    var maxResults: Int = MAX_RESULTS_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_EFFICIENTDETV0,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val objectDetectorListener: DetectorListener? = null
) {

    private var objectDetector: ObjectDetector? = null
    private var imageRotation = 0
    private lateinit var imageProcessingOptions: ImageProcessingOptions

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector?.close()
        objectDetector = null
    }

    fun setupObjectDetector() {
        val baseOptionsBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
        }

        val modelName = when (currentModel) {
            MODEL_EFFICIENTDETV0 -> "object_detection/efficientdet_lite0.tflite"
            MODEL_EFFICIENTDETV2 -> "object_detection/efficientdet_lite2.tflite"
            else -> "object_detection/efficientdet_lite0.tflite"
        }
        baseOptionsBuilder.setModelAssetPath(modelName)

        // Config
        val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setScoreThreshold(threshold)
            .setMaxResults(maxResults)
            .setRunningMode(runningMode)

        // Running mode
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (objectDetectorListener == null) {
                    throw IllegalStateException("objectDetectorListener es null en LIVE_STREAM")
                }
                optionsBuilder
                    .setResultListener(::returnLivestreamResult)
                    .setErrorListener(::returnLivestreamError)
            }
            else -> {}
        }

        imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(imageRotation).build()

        try {
            val options = optionsBuilder.build()
            objectDetector = ObjectDetector.createFromOptions(context, options)
        } catch (e: Exception) {
            objectDetectorListener?.onError("Error al inicializar detector de objetos: ${e.message}", GPU_ERROR)
            Log.e(TAG, "Error: ${e.message}")
        }
    }

    fun isClosed(): Boolean = (objectDetector == null)

    // Video
    fun detectVideoFile(videoUri: Uri, intervalMs: Long): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException("RunningMode debe ser VIDEO para detectVideoFile()")
        }
        if (objectDetector == null) return null

        val startTime = SystemClock.uptimeMillis()
        var errorOccurred = false
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height
        if (videoLengthMs == null || width == null || height == null) return null

        val resultList = mutableListOf<ObjectDetectorResult>()
        val frameCount = videoLengthMs / intervalMs

        for (i in 0..frameCount) {
            val timestampMs = i * intervalMs
            val frame = retriever.getFrameAtTime(timestampMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
            frame?.let {
                val argbFrame = if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false)
                val mpImage = BitmapImageBuilder(argbFrame).build()
                val detection = objectDetector?.detectForVideo(mpImage, timestampMs)
                if (detection != null) {
                    resultList.add(detection)
                } else {
                    errorOccurred = true
                }
            } ?: run { errorOccurred = true }
        }
        retriever.release()
        val inferenceTime = (SystemClock.uptimeMillis() - startTime) / (frameCount + 1)

        return if (errorOccurred) null else ResultBundle(resultList, inferenceTime, height, width)
    }

    // LiveStream
    fun detectLivestreamFrame(imageProxy: ImageProxy) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException("RunningMode debe ser LIVE_STREAM para detectLivestreamFrame()")
        }

        val frameTime = SystemClock.uptimeMillis()

        val bitmapBuffer = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        imageProxy.close()

        if (rotationDegrees != imageRotation) {
            imageRotation = rotationDegrees
            clearObjectDetector()
            setupObjectDetector()
            return
        }

        val mpImage = BitmapImageBuilder(bitmapBuffer).build()
        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        objectDetector?.detectAsync(mpImage, imageProcessingOptions, frameTime)
    }

    private fun returnLivestreamResult(result: ObjectDetectorResult, input: MPImage) {
        val inferenceTime = SystemClock.uptimeMillis() - result.timestampMs()
        objectDetectorListener?.onResults(
            ResultBundle(listOf(result), inferenceTime, input.height, input.width, imageRotation)
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        objectDetectorListener?.onError(error.message ?: "Error desconocido", OTHER_ERROR)
    }

    // Single Image
    fun detectImage(bitmap: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException("RunningMode debe ser IMAGE para detectImage()")
        }
        if (objectDetector == null) return null
        val startTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(bitmap).build()
        val detectionResult = objectDetector?.detect(mpImage) ?: return null
        val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
        return ResultBundle(listOf(detectionResult), inferenceTimeMs, bitmap.height, bitmap.width)
    }

    data class ResultBundle(
        val results: List<ObjectDetectorResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val inputImageRotation: Int = 0
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_EFFICIENTDETV0 = 0
        const val MODEL_EFFICIENTDETV2 = 1
        const val MAX_RESULTS_DEFAULT = 3
        const val THRESHOLD_DEFAULT = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val TAG = "ObjectDetectorHelper"
    }

    interface DetectorListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
