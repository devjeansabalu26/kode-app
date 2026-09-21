package com.kode.app.kode_app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.repository.AuthRepository
import com.kode.app.kode_app.databinding.FragmentLoginBinding
import com.kode.app.kode_app.ui.navigateTo
import com.kode.app.kode_app.ui.showError
import com.kode.app.kode_app.ui.showMessage
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null

    private val binding get() = _binding!!

    private val authRepository = AuthRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            if (email.isEmpty()) {
                showMessage("Ingresa tu correo")
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                showMessage("Ingresa tu contraseña")
                return@setOnClickListener
            }
            binding.btnLogin.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = authRepository.login(email, password)) {
                    is AppResult.Failure -> {
                        showError(result.error)
                        binding.btnLogin.isEnabled = true
                    }
                    is AppResult.Success -> {
                        showMessage("Bienvenido ${SessionManager().getName()}")
                        continuePendingAction()
                    }
                }
            }
        }
        binding.tvRegister.setOnClickListener {
            navigateTo(
                RegisterFragment.newInstance(
                    destination = arguments?.getString(ARG_DESTINATION),
                    eventId = arguments?.getLong(ARG_EVENT_ID, -1L) ?: -1L,
                    eventTitle = arguments?.getString(ARG_EVENT_TITLE)
                ),
                addToBackStack = true
            )
        }
    }

    companion object {
        fun newInstance(destination: String?, eventId: Long = -1L, eventTitle: String? = null): LoginFragment {
            return LoginFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESTINATION, destination)
                    putLong(ARG_EVENT_ID, eventId)
                    putString(ARG_EVENT_TITLE, eventTitle)
                }
            }
        }
    }
}
