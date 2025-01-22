//PermissionsFragment
package com.example.visionfusion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.visionfusion.R

private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

/**
 * Fragment para solicitar permisos de cámara.
 */
class PermissionsFragment : Fragment() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(context, "Permiso de cámara concedido", Toast.LENGTH_LONG).show()
                navigateToObjectCamera()
            } else {
                Toast.makeText(context, "Permiso de cámara denegado", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                navigateToObjectCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun navigateToObjectCamera() {
        lifecycleScope.launchWhenStarted {
            // Navegamos al fragment de ObjectCamera por defecto
            findNavController().navigate(R.id.action_permissionsFragment_to_objectCameraFragment)
        }
    }

    companion object {
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
