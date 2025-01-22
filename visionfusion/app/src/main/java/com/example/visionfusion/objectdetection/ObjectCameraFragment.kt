package com.example.visionfusion.objectdetection

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.visionfusion.MainViewModel
import com.example.visionfusion.R
import com.example.visionfusion.TtsManager
import com.example.visionfusion.databinding.FragmentCameraObjectBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ObjectCameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    private var _binding: FragmentCameraObjectBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var objectDetectorHelper: ObjectDetectorHelper

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var backgroundExecutor: ExecutorService

    // TTS
    private var ttsManager: TtsManager? = null
    private var lastSpokenText: String? = null
    private var lastSpokenTime: Long = 0
    private val speakInterval = 1500L

    // Por defecto, lente trasera
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraObjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // TTS
        ttsManager = TtsManager(requireContext())

        // Botón para cambiar lente
        val switchCamButton = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            layoutParams = ConstraintLayout.LayoutParams(120, 120).also {
                it.endToEnd = binding.root.id
                it.topToTop = binding.root.id
                it.setMargins(16, 16, 16, 16)
            }
            setOnClickListener {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
                bindCameraUseCases()
            }
        }
        (binding.root as ConstraintLayout).addView(switchCamButton)

        // FAB para galería (opcional)
        /*
        binding.fabOpenGallery.setOnClickListener {
            findNavController().navigate(R.id.objectGalleryFragment)
        }
        */

        backgroundExecutor.execute {
            objectDetectorHelper = ObjectDetectorHelper(
                context = requireContext(),
                threshold = viewModel.objectThreshold,
                maxResults = viewModel.objectMaxResults,
                currentDelegate = viewModel.objectDelegate,
                currentModel = viewModel.objectModel,
                runningMode = RunningMode.LIVE_STREAM,
                objectDetectorListener = this
            )
            binding.viewFinder.post { setUpCamera() }
        }

        initUiControls()
        binding.overlay.setRunningMode(RunningMode.LIVE_STREAM)
    }

    override fun onResume() {
        super.onResume()
        backgroundExecutor.execute {
            if (objectDetectorHelper.isClosed()) {
                objectDetectorHelper.setupObjectDetector()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.setObjectThreshold(objectDetectorHelper.threshold)
        viewModel.setObjectMaxResults(objectDetectorHelper.maxResults)
        viewModel.setObjectDelegate(objectDetectorHelper.currentDelegate)
        viewModel.setObjectModel(objectDetectorHelper.currentModel)

        backgroundExecutor.execute {
            objectDetectorHelper.clearObjectDetector()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager?.shutDown()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        _binding = null
    }

    private fun initUiControls() {
        binding.bottomSheetLayout.thresholdValue.text = String.format("%.2f", viewModel.objectThreshold)
        binding.bottomSheetLayout.maxResultsValue.text = viewModel.objectMaxResults.toString()

        // Botones threshold
        binding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (objectDetectorHelper.threshold >= 0.1f) {
                objectDetectorHelper.threshold -= 0.1f
                updateDetector()
            }
        }
        binding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (objectDetectorHelper.threshold <= 0.9f) {
                objectDetectorHelper.threshold += 0.1f
                updateDetector()
            }
        }

        // Botones maxResults
        binding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
            if (objectDetectorHelper.maxResults > 1) {
                objectDetectorHelper.maxResults--
                updateDetector()
            }
        }
        binding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
            if (objectDetectorHelper.maxResults < 5) {
                objectDetectorHelper.maxResults++
                updateDetector()
            }
        }
    }

    private fun updateDetector() {
        binding.bottomSheetLayout.thresholdValue.text = String.format("%.2f", objectDetectorHelper.threshold)
        binding.bottomSheetLayout.maxResultsValue.text = objectDetectorHelper.maxResults.toString()

        backgroundExecutor.execute {
            objectDetectorHelper.clearObjectDetector()
            objectDetectorHelper.setupObjectDetector()
        }
        binding.overlay.clear()
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(binding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build().also {
                it.setAnalyzer(backgroundExecutor, objectDetectorHelper::detectLivestreamFrame)
            }

        try {
            camera = provider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e("ObjectCameraFragment", "Use case binding failed", exc)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = binding.viewFinder.display.rotation
    }

    override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
        activity?.runOnUiThread {
            binding.bottomSheetLayout.inferenceTimeVal.text = "${resultBundle.inferenceTime} ms"

            val detectionResult = resultBundle.results[0]
            binding.overlay.setResults(
                detectionResult,
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth,
                resultBundle.inputImageRotation
            )
            binding.overlay.invalidate()

            // TTS
            val categories = detectionResult.detections().map { det ->
                det.categories()[0].categoryName()
            }.distinct()

            if (categories.isNotEmpty()) {
                val speakText = categories.joinToString(", ")
                val now = System.currentTimeMillis()
                if (speakText != lastSpokenText || (now - lastSpokenTime) > speakInterval) {
                    ttsManager?.speak("Veo: $speakText")
                    lastSpokenText = speakText
                    lastSpokenTime = now
                }
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}
