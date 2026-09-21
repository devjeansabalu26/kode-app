package com.kode.app.kode_app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.core.AppError
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.repository.AuthRepository
import com.kode.app.kode_app.databinding.FragmentProfileBinding
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.navigateTo
import com.kode.app.kode_app.ui.showError
import com.kode.app.kode_app.ui.showMessage
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null

    private val binding get() = _binding!!

    private val authRepository = AuthRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!SessionManager().isLoggedIn()) {
            openLogin()
            return
        }
        binding.btnLogout.setOnClickListener {
            binding.btnLogout.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                authRepository.logout()
                showMessage("Sesión cerrada")
                openLogin()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = authRepository.getCurrentProfile()) {
                is AppResult.Success -> {
                    val user = result.value
                    binding.tvProfileName.text = user.name
                    binding.tvProfileEmail.text = user.email
                    binding.tvProfileDni.text = "DNI: ${user.dni}"
                    binding.tvProfilePhone.text = "Celular: ${user.phone}"
                    binding.tvProfileGender.text = "Género: ${user.gender}"
                    binding.tvProfileAge.text = "Edad: ${user.age}"
                }
                is AppResult.Failure -> {
                    showError(result.error)
                    if (result.error == AppError.NOT_AUTHENTICATED) openLogin()
                }
            }
        }
    }

    private fun openLogin() {
        navigateTo(LoginFragment.newInstance(destination = "PROFILE"))
    }
}
