package com.example.visionfusion.facedetection

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
import com.example.visionfusion.databinding.FragmentCameraFaceBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FaceCameraFragment : Fragment(), FaceDetectorHelper.DetectorListener {

    private var _binding: FragmentCameraFaceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var faceDetectorHelper: FaceDetectorHelper

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var backgroundExecutor: ExecutorService

    // TTS
    private var ttsManager: TtsManager? = null
    private var lastSpokenTime: Long = 0
    private val speakInterval = 1500L

    // Lente actual (por defecto frontal)
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraFaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // TTS
        ttsManager = TtsManager(requireContext())

        // 1) Botón para alternar la cámara:
        val switchCamButton = ImageButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            layoutParams = ConstraintLayout.LayoutParams(120, 120).also {
                it.endToEnd = binding.faceCameraRoot.id
                it.topToTop = binding.faceCameraRoot.id
                it.setMargins(16, 16, 16, 16)
            }
            setOnClickListener {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                bindCameraUseCases()
            }
        }
        (binding.faceCameraRoot as ConstraintLayout).addView(switchCamButton)

        // 2) Si quisieras un FAB para la galería (opcional)
        //    Descomenta si lo has definido en fragment_camera_face.xml
        /*
        binding.fabOpenGallery.setOnClickListener {
            // Navegar a FaceGalleryFragment
            findNavController().navigate(R.id.faceGalleryFragment)
        }
        */

        // Creamos FaceDetectorHelper
        backgroundExecutor.execute {
            faceDetectorHelper = FaceDetectorHelper(
                context = requireContext(),
                threshold = viewModel.faceThreshold,
                currentDelegate = viewModel.faceDelegate,
                runningMode = RunningMode.LIVE_STREAM,
                faceDetectorListener = this
            )
            binding.viewFinderFace.post { setUpCamera() }
        }

        initUiControls()
    }

    private fun initUiControls() {
        binding.bottomSheetLayoutFace.thresholdValue.text =
            String.format("%.2f", viewModel.faceThreshold)

        // Botones +/- threshold
        binding.bottomSheetLayoutFace.thresholdMinus.setOnClickListener {
            if (faceDetectorHelper.threshold >= 0.1f) {
                faceDetectorHelper.threshold -= 0.1f
                reloadDetector()
            }
        }
        binding.bottomSheetLayoutFace.thresholdPlus.setOnClickListener {
            if (faceDetectorHelper.threshold <= 0.9f) {
                faceDetectorHelper.threshold += 0.1f
                reloadDetector()
            }
        }
    }

    private fun reloadDetector() {
        binding.bottomSheetLayoutFace.thresholdValue.text =
            String.format("%.2f", faceDetectorHelper.threshold)
        backgroundExecutor.execute {
            faceDetectorHelper.clearFaceDetector()
            faceDetectorHelper.setupFaceDetector()
        }
        binding.overlayFace.clear()
    }

    override fun onResume() {
        super.onResume()
        backgroundExecutor.execute {
            if (faceDetectorHelper.isClosed()) {
                faceDetectorHelper.setupFaceDetector()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.setFaceThreshold(faceDetectorHelper.threshold)
        viewModel.setFaceDelegate(faceDetectorHelper.currentDelegate)

        backgroundExecutor.execute {
            faceDetectorHelper.clearFaceDetector()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager?.shutDown()

        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        _binding = null
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
            .setTargetRotation(binding.viewFinderFace.display.rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(binding.viewFinderFace.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build().also { analysis ->
                analysis.setAnalyzer(backgroundExecutor, faceDetectorHelper::detectLivestreamFrame)
            }

        try {
            camera = provider.bindToLifecycle(
                viewLifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            preview?.setSurfaceProvider(binding.viewFinderFace.surfaceProvider)
        } catch (e: Exception) {
            Log.e("FaceCameraFragment", "Camera binding failed", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = binding.viewFinderFace.display.rotation
    }

    // FaceDetectorHelper.DetectorListener
    override fun onResults(resultBundle: FaceDetectorHelper.ResultBundle) {
        activity?.runOnUiThread {
            // Inference time
            binding.bottomSheetLayoutFace.inferenceTimeVal.text =
                "${resultBundle.inferenceTime} ms"

            val faceResult = resultBundle.results[0]
            binding.overlayFace.setResults(
                faceResult,
                resultBundle.inputImageHeight,
                resultBundle.inputImageWidth
            )
            binding.overlayFace.invalidate()

            // TTS
            val faceCount = faceResult.detections().size
            if (faceCount > 0) {
                val now = System.currentTimeMillis()
                if (now - lastSpokenTime > speakInterval) {
                    ttsManager?.speak("He detectado $faceCount rostro(s).")
                    lastSpokenTime = now
                }
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "FaceDetector Error: $error", Toast.LENGTH_SHORT).show()
        }
    }
}
