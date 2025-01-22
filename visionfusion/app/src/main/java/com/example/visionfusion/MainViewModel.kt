//MainViewModel

package com.example.visionfusion

import androidx.lifecycle.ViewModel
import com.example.visionfusion.facedetection.FaceDetectorHelper
import com.example.visionfusion.objectdetection.ObjectDetectorHelper

class MainViewModel : ViewModel() {

    // =====================
    //       FACE
    // =====================

    // Propiedad interna para almacenar el threshold de Face
    private var internalFaceThreshold: Float = FaceDetectorHelper.THRESHOLD_DEFAULT
    // Propiedad pública de solo lectura para leer faceThreshold
    val faceThreshold: Float
        get() = internalFaceThreshold

    // Mantengo la función setFaceThreshold(...) con el mismo nombre
    fun setFaceThreshold(value: Float) {
        internalFaceThreshold = value
    }

    // Delegate de Face
    private var internalFaceDelegate: Int = FaceDetectorHelper.DELEGATE_CPU
    val faceDelegate: Int
        get() = internalFaceDelegate

    fun setFaceDelegate(value: Int) {
        internalFaceDelegate = value
    }

    // =====================
    //       OBJECT
    // =====================

    private var internalObjectThreshold: Float = ObjectDetectorHelper.THRESHOLD_DEFAULT
    val objectThreshold: Float
        get() = internalObjectThreshold

    fun setObjectThreshold(value: Float) {
        internalObjectThreshold = value
    }

    private var internalObjectMaxResults: Int = ObjectDetectorHelper.MAX_RESULTS_DEFAULT
    val objectMaxResults: Int
        get() = internalObjectMaxResults

    fun setObjectMaxResults(value: Int) {
        internalObjectMaxResults = value
    }

    private var internalObjectDelegate: Int = ObjectDetectorHelper.DELEGATE_CPU
    val objectDelegate: Int
        get() = internalObjectDelegate

    fun setObjectDelegate(value: Int) {
        internalObjectDelegate = value
    }

    private var internalObjectModel: Int = ObjectDetectorHelper.MODEL_EFFICIENTDETV0
    val objectModel: Int
        get() = internalObjectModel

    fun setObjectModel(value: Int) {
        internalObjectModel = value
    }
}
