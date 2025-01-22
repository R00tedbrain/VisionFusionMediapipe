//MainActivity
package com.example.visionfusion

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.visionfusion.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup bottom navigation
        val navController = findNavController(R.id.fragment_container)
        binding.navigation.setupWithNavController(navController)

        // Controlar reselección (opcional)
        binding.navigation.setOnNavigationItemReselectedListener {
            // Ignorar reselecciones
        }
    }

    override fun onBackPressed() {
        // Si deseas finalizar la app al presionar back en vez de hacer pop de fragments
        // super.onBackPressed()
        finish()
    }
}
