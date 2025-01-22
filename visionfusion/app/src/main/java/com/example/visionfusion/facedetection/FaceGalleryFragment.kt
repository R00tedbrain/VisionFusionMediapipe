//FaecGalleryFragment
package com.example.visionfusion.facedetection

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.visionfusion.MainViewModel
import com.example.visionfusion.databinding.FragmentGalleryBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class FaceGalleryFragment : Fragment(), FaceDetectorHelper.DetectorListener {

    // Tipos de medio
    enum class MediaType { IMAGE, VIDEO, UNKNOWN }

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var faceDetectorHelper: FaceDetectorHelper
    private lateinit var backgroundExecutor: ScheduledExecutorService

    // ViewModel con (faceThreshold, faceDelegate, etc.)
    private val viewModel: MainViewModel by activityViewModels()

    // Contrato para abrir imagen/video
    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { mediaUri ->
                when (val mediaType = loadMediaType(mediaUri)) {
                    MediaType.IMAGE -> runDetectionOnImage(mediaUri)
                    MediaType.VIDEO -> runDetectionOnVideo(mediaUri)
                    MediaType.UNKNOWN -> {
                        updateDisplayView(mediaType)
                        Toast.makeText(
                            requireContext(),
                            "Unsupported data type.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Setup inicial de UI
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // FAB: seleccionar archivo
        binding.fabGetContent.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
        }

        initBottomSheetControls()
    }

    override fun onPause() {
        super.onPause()
        // Limpia overlay y detiene video
        binding.overlay.clear()
        if (binding.videoView.isPlaying) binding.videoView.stopPlayback()
        binding.videoView.visibility = View.GONE
    }

    private fun initBottomSheetControls() {
        updateControlsUi()

        // Botones threshold
        binding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (viewModel.faceThreshold >= 0.1f) {
                viewModel.setFaceThreshold(viewModel.faceThreshold - 0.1f)
                updateControlsUi()
            }
        }
        binding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (viewModel.faceThreshold <= 0.8f) {
                viewModel.setFaceThreshold(viewModel.faceThreshold + 0.1f)
                updateControlsUi()
            }
        }

        // Spinner delegate para face
        binding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.faceDelegate,
            false
        )
        binding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long
                ) {
                    // Cambiamos faceDelegate
                    viewModel.setFaceDelegate(position)
                    updateControlsUi()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun updateControlsUi() {
        if (binding.videoView.isPlaying) binding.videoView.stopPlayback()
        binding.videoView.visibility = View.GONE
        binding.imageResult.visibility = View.GONE
        binding.overlay.clear()

        // Muestra faceThreshold
        binding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", viewModel.faceThreshold)

        binding.tvPlaceholder.visibility = View.VISIBLE
    }

    private fun runDetectionOnImage(uri: Uri) {
        setUiEnabled(false)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        updateDisplayView(MediaType.IMAGE)

        val orig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(
                requireActivity().contentResolver,
                uri
            )
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(
                requireActivity().contentResolver,
                uri
            )
        }

        val bitmap = orig?.copy(Bitmap.Config.ARGB_8888, true)
        bitmap?.let {
            binding.imageResult.setImageBitmap(it)

            backgroundExecutor.execute {
                faceDetectorHelper = FaceDetectorHelper(
                    context = requireContext(),
                    threshold = viewModel.faceThreshold,
                    currentDelegate = viewModel.faceDelegate,
                    runningMode = RunningMode.IMAGE,
                    faceDetectorListener = this
                )

                faceDetectorHelper.detectImage(it)?.let { resultBundle ->
                    activity?.runOnUiThread {
                        // Llamada a setResults con 3 params (FaceDetectorResult, height, width)
                        binding.overlay.setResults(
                            resultBundle.results[0],
                            it.height,
                            it.width
                        )
                        setUiEnabled(true)
                        binding.bottomSheetLayout.inferenceTimeVal.text =
                            String.format("%d ms", resultBundle.inferenceTime)
                    }
                } ?: run {
                    Log.e(TAG, "Error running face detection.")
                }

                faceDetectorHelper.clearFaceDetector()
            }
        }
    }

    private fun runDetectionOnVideo(uri: Uri) {
        setUiEnabled(false)
        updateDisplayView(MediaType.VIDEO)

        with(binding.videoView) {
            setVideoURI(uri)
            // silenciamo audio
            setOnPreparedListener { it.setVolume(0f, 0f) }
            requestFocus()
        }

        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor.execute {
            faceDetectorHelper = FaceDetectorHelper(
                context = requireContext(),
                threshold = viewModel.faceThreshold,
                currentDelegate = viewModel.faceDelegate,
                runningMode = RunningMode.VIDEO,
                faceDetectorListener = this
            )

            activity?.runOnUiThread {
                binding.videoView.visibility = View.GONE
                binding.progress.visibility = View.VISIBLE
            }

            faceDetectorHelper.detectVideoFile(uri, VIDEO_INTERVAL_MS)?.let { result ->
                activity?.runOnUiThread {
                    displayVideoResult(result)
                }
            } ?: run {
                activity?.runOnUiThread {
                    binding.progress.visibility = View.GONE
                }
                Log.e(TAG, "Error running face detection.")
            }

            faceDetectorHelper.clearFaceDetector()
        }
    }

    private fun displayVideoResult(result: FaceDetectorHelper.ResultBundle) {
        binding.videoView.visibility = View.VISIBLE
        binding.progress.visibility = View.GONE
        binding.videoView.start()

        val startMs = SystemClock.uptimeMillis()

        backgroundExecutor.scheduleAtFixedRate({
            activity?.runOnUiThread {
                val elapsedMs = SystemClock.uptimeMillis() - startMs
                val frameIndex = (elapsedMs / VIDEO_INTERVAL_MS).toInt()

                if (frameIndex >= result.results.size ||
                    binding.videoView.visibility == View.GONE
                ) {
                    backgroundExecutor.shutdown()
                } else {
                    // También 3 params
                    binding.overlay.setResults(
                        result.results[frameIndex],
                        result.inputImageHeight,
                        result.inputImageWidth
                    )
                    setUiEnabled(true)
                    binding.bottomSheetLayout.inferenceTimeVal.text =
                        String.format("%d ms", result.inferenceTime)
                }
            }
        }, 0, VIDEO_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun updateDisplayView(mediaType: MediaType) {
        binding.overlay.clear()
        binding.imageResult.visibility =
            if (mediaType == MediaType.IMAGE) View.VISIBLE else View.GONE
        binding.videoView.visibility =
            if (mediaType == MediaType.VIDEO) View.VISIBLE else View.GONE
        binding.tvPlaceholder.visibility =
            if (mediaType == MediaType.UNKNOWN) View.VISIBLE else View.GONE
    }

    private fun loadMediaType(uri: Uri): MediaType {
        val mimeType = context?.contentResolver?.getType(uri)
        return when {
            mimeType?.startsWith("image") == true -> MediaType.IMAGE
            mimeType?.startsWith("video") == true -> MediaType.VIDEO
            else -> MediaType.UNKNOWN
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.fabGetContent.isEnabled = enabled
        binding.bottomSheetLayout.thresholdMinus.isEnabled = enabled
        binding.bottomSheetLayout.thresholdPlus.isEnabled = enabled
        // spinnerDelegate sin maxResults en Face
        binding.bottomSheetLayout.spinnerDelegate.isEnabled = enabled
    }

    private fun detectError() {
        activity?.runOnUiThread {
            binding.progress.visibility = View.GONE
            setUiEnabled(true)
            updateDisplayView(MediaType.UNKNOWN)
        }
    }

    // FaceDetectorHelper.DetectorListener
    override fun onError(error: String, errorCode: Int) {
        detectError()
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "FaceDetector Error: $error", Toast.LENGTH_SHORT).show()
            if (errorCode == FaceDetectorHelper.GPU_ERROR) {
                // spinnerDelegate
                binding.bottomSheetLayout.spinnerDelegate.setSelection(
                    FaceDetectorHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    override fun onResults(resultBundle: FaceDetectorHelper.ResultBundle) {
        // no-op (solo en RunningMode.LIVE_STREAM)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "FaceGalleryFragment"
        private const val VIDEO_INTERVAL_MS = 300L
    }
}
