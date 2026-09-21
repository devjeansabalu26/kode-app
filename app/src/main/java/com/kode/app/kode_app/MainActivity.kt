package com.kode.app.kode_app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.repository.AuthRepository
import com.kode.app.kode_app.databinding.ActivityMainBinding
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.events.EventFormFragment
import com.kode.app.kode_app.ui.events.MyEventsFragment
import com.kode.app.kode_app.ui.home.HomeFragment
import com.kode.app.kode_app.ui.profile.ProfileFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    openFragment(HomeFragment())
                    true
                }
                R.id.nav_my_events -> {
                    openFragment(MyEventsFragment())
                    true
                }
                R.id.nav_create_event -> {
                    if (SessionManager().isLoggedIn()) {
                        openFragment(EventFormFragment())
                    } else {
                        openFragment(LoginFragment.newInstance(destination = "CREATE_EVENT"))
                    }
                    true
                }
                R.id.nav_profile -> {
                    openFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
        if (savedInstanceState == null) {
            // Se espera a que el SDK de Supabase cargue la sesión guardada antes de mostrar Inicio.
            lifecycleScope.launch {
                AuthRepository().awaitReady()
                binding.bottomNavigation.selectedItemId = R.id.nav_home
            }
        }
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.mainContainer, fragment).commit()
    }
}
