package com.kode.app.kode_app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.kode.app.kode_app.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.mainContainer) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
        // Al tocar la pestaña ya seleccionada (p. ej. Inicio estando en un detalle), volver a su pantalla raíz
        binding.bottomNavigation.setOnItemReselectedListener { item ->
            navController.popBackStack(item.itemId, false)
        }
    }
}
