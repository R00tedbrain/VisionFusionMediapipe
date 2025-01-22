//ObjectGalleryFragment
package com.example.visionfusion.objectdetection

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
import com.example.visionfusion.databinding.FragmentGalleryObjectBinding // <--- O el nombre que corresponda
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ObjectGalleryFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    enum class MediaType { IMAGE, VIDEO, UNKNOWN }

    private var _binding: FragmentGalleryObjectBinding? = null // <--- tu Binding
    private val binding get() = _binding!!

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var backgroundExecutor: ScheduledExecutorService
    private val viewModel: MainViewModel by activityViewModels()

    // Contrato para abrir imágenes / videos
    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { mediaUri ->
                when (val type = loadMediaType(mediaUri)) {
                    MediaType.IMAGE -> runDetectionOnImage(mediaUri)
                    MediaType.VIDEO -> runDetectionOnVideo(mediaUri)
                    MediaType.UNKNOWN -> {
                        updateDisplayView(type)
                        Toast.makeText(requireContext(),
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
        // Infla el layout que contiene <ObjectOverlayView>
        _binding = FragmentGalleryObjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fabGetContent.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
        }
        initBottomSheetControls()
    }

    override fun onPause() {
        super.onPause()
        binding.overlay.clear()
        if (binding.videoView.isPlaying) binding.videoView.stopPlayback()
        binding.videoView.visibility = View.GONE
    }

    private fun initBottomSheetControls() {
        updateControlsUi()

        // Umbral
        binding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (viewModel.objectThreshold >= 0.1f) {
                viewModel.setObjectThreshold(viewModel.objectThreshold - 0.1f)
                updateControlsUi()
            }
        }
        binding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (viewModel.objectThreshold <= 0.8f) {
                viewModel.setObjectThreshold(viewModel.objectThreshold + 0.1f)
                updateControlsUi()
            }
        }

        // maxResults
        binding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
            if (viewModel.objectMaxResults > 1) {
                viewModel.setObjectMaxResults(viewModel.objectMaxResults - 1)
                updateControlsUi()
            }
        }
        binding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
            if (viewModel.objectMaxResults < 5) {
                viewModel.setObjectMaxResults(viewModel.objectMaxResults + 1)
                updateControlsUi()
            }
        }

        // Delegate (CPU / GPU / NNAPI)
        binding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.objectDelegate, false
        )
        binding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, pos: Int, id: Long
                ) {
                    viewModel.setObjectDelegate(pos)
                    updateControlsUi()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        // Modelo (efficientdet0, 2, etc.)
        binding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.objectModel, false
        )
        binding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, pos: Int, id: Long
                ) {
                    viewModel.setObjectModel(pos)
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

        binding.bottomSheetLayout.maxResultsValue.text =
            viewModel.objectMaxResults.toString()
        binding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", viewModel.objectThreshold)

        binding.overlay.clear()
        binding.tvPlaceholder.visibility = View.VISIBLE
    }

    private fun runDetectionOnImage(uri: Uri) {
        // Corregir: RunningMode.IMAGE
        binding.overlay.setRunningMode(RunningMode.IMAGE)

        setUiEnabled(false)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        updateDisplayView(MediaType.IMAGE)

        val srcBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(requireActivity().contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, uri)
        }

        val bitmap = srcBitmap?.copy(Bitmap.Config.ARGB_8888, true)
        bitmap?.let {
            binding.imageResult.setImageBitmap(it)
            backgroundExecutor.execute {
                // Crea objectDetectorHelper
                objectDetectorHelper = ObjectDetectorHelper(
                    context = requireContext(),
                    threshold = viewModel.objectThreshold,
                    currentDelegate = viewModel.objectDelegate,
                    currentModel = viewModel.objectModel,
                    maxResults = viewModel.objectMaxResults,
                    runningMode = RunningMode.IMAGE,
                    objectDetectorListener = this
                )

                val result = objectDetectorHelper.detectImage(it)
                if (result != null) {
                    activity?.runOnUiThread {
                        binding.overlay.setResults(
                            result.results[0],
                            it.height,
                            it.width,
                            result.inputImageRotation
                        )
                        setUiEnabled(true)
                        binding.bottomSheetLayout.inferenceTimeVal.text =
                            String.format("%d ms", result.inferenceTime)
                    }
                } else {
                    Log.e(TAG, "Error running object detection.")
                }
                objectDetectorHelper.clearObjectDetector()
            }
        }
    }

    private fun runDetectionOnVideo(uri: Uri) {
        binding.overlay.setRunningMode(RunningMode.VIDEO)

        setUiEnabled(false)
        updateDisplayView(MediaType.VIDEO)
        with(binding.videoView) {
            setVideoURI(uri)
            setOnPreparedListener { it.setVolume(0f, 0f) }
            requestFocus()
        }

        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor.execute {
            objectDetectorHelper = ObjectDetectorHelper(
                context = requireContext(),
                threshold = viewModel.objectThreshold,
                currentDelegate = viewModel.objectDelegate,
                currentModel = viewModel.objectModel,
                maxResults = viewModel.objectMaxResults,
                runningMode = RunningMode.VIDEO,
                objectDetectorListener = this
            )

            activity?.runOnUiThread {
                binding.videoView.visibility = View.GONE
                binding.progress.visibility = View.VISIBLE
            }

            val resultBundle = objectDetectorHelper.detectVideoFile(uri, VIDEO_INTERVAL_MS)
            if (resultBundle != null) {
                activity?.runOnUiThread { displayVideoResult(resultBundle) }
            } else {
                activity?.runOnUiThread { binding.progress.visibility = View.GONE }
                Log.e(TAG, "Error running object detection.")
            }
            objectDetectorHelper.clearObjectDetector()
        }
    }

    private fun displayVideoResult(result: ObjectDetectorHelper.ResultBundle) {
        binding.videoView.visibility = View.VISIBLE
        binding.progress.visibility = View.GONE
        binding.videoView.start()

        val videoStartTime = SystemClock.uptimeMillis()
        backgroundExecutor.scheduleAtFixedRate({
            activity?.runOnUiThread {
                val elapsed = SystemClock.uptimeMillis() - videoStartTime
                val frameIndex = (elapsed / VIDEO_INTERVAL_MS).toInt()

                if (frameIndex >= result.results.size || binding.videoView.visibility == View.GONE) {
                    backgroundExecutor.shutdown()
                } else {
                    binding.overlay.setResults(
                        result.results[frameIndex],
                        result.inputImageHeight,
                        result.inputImageWidth,
                        result.inputImageRotation
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
        val mime = context?.contentResolver?.getType(uri)
        mime?.let {
            if (it.startsWith("image")) return MediaType.IMAGE
            if (it.startsWith("video")) return MediaType.VIDEO
        }
        return MediaType.UNKNOWN
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.fabGetContent.isEnabled = enabled
        binding.bottomSheetLayout.spinnerModel.isEnabled = enabled
        binding.bottomSheetLayout.thresholdMinus.isEnabled = enabled
        binding.bottomSheetLayout.thresholdPlus.isEnabled = enabled
        binding.bottomSheetLayout.maxResultsMinus.isEnabled = enabled
        binding.bottomSheetLayout.maxResultsPlus.isEnabled = enabled
        binding.bottomSheetLayout.spinnerDelegate.isEnabled = enabled
    }

    private fun detectError() {
        activity?.runOnUiThread {
            binding.progress.visibility = View.GONE
            setUiEnabled(true)
            updateDisplayView(MediaType.UNKNOWN)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        detectError()
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == ObjectDetectorHelper.GPU_ERROR) {
                // Forzamos spinnerDelegate a CPU
                binding.bottomSheetLayout.spinnerDelegate.setSelection(
                    ObjectDetectorHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
        // no-op
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ObjectGalleryFragment"
        private const val VIDEO_INTERVAL_MS = 300L
    }
}
