package com.kode.app.kode_app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.databinding.FragmentLoginBinding
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.RegistrationRepository
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.ui.events.EventFormFragment
import com.kode.app.kode_app.ui.events.MyEventsFragment
import com.kode.app.kode_app.ui.events.TicketFragment
import com.kode.app.kode_app.ui.home.HomeFragment
import com.kode.app.kode_app.ui.profile.ProfileFragment

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null

    private val binding get() = _binding!!

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
        val etEmail = binding.etEmail
        val etPassword = binding.etPassword
        val btnLogin = binding.btnLogin
        val tvRegister = binding.tvRegister
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            if (email.isEmpty()) {
                showMessage("Ingresa tu correo")
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                showMessage("Ingresa tu contraseña")
                return@setOnClickListener
            }
            val userRepository = UserRepository(requireContext())
            val user = userRepository.login(email = email, password = password)
            if (user == null) {
                showMessage("Correo o contraseña incorrectos")
                return@setOnClickListener
            }
            SessionManager(requireContext()).createSession(userId = user.id, name = user.name, email = user.email)
            showMessage("Bienvenido ${user.name}")
            continuePendingAction(registrationRepository = RegistrationRepository(requireContext()), userId = user.id)
        }
        tvRegister.setOnClickListener {
            parentFragmentManager.beginTransaction().replace(
                R.id.mainContainer,
                RegisterFragment.newInstance(
                    destination = arguments?.getString(ARG_DESTINATION),
                    eventId = arguments?.getInt(ARG_EVENT_ID) ?: -1,
                    eventTitle = arguments?.getString(ARG_EVENT_TITLE)
                )
            ).addToBackStack(null).commit()
        }
    }

    private fun continuePendingAction(registrationRepository: RegistrationRepository, userId: Long) {
        val destination = arguments?.getString(ARG_DESTINATION)
        when (destination) {
            "REGISTER_EVENT" -> {
                val eventId = arguments?.getInt(ARG_EVENT_ID) ?: -1
                val eventTitle = arguments?.getString(ARG_EVENT_TITLE) ?: ""
                if (eventId == -1) {
                    showMessage("No se pudo identificar el evento")
                    return
                }
                val qrCode = registrationRepository.registerUserInEvent(eventId = eventId, userId = userId)
                if (qrCode == null) {
                    showMessage("No se pudo realizar la inscripción")
                    return
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.mainContainer, TicketFragment.newInstance(eventId = eventId, eventTitle = eventTitle, qrCode = qrCode)).commit()
            }
            "CREATE_EVENT" -> {
                parentFragmentManager.beginTransaction().replace(R.id.mainContainer, EventFormFragment()).commit()
            }
            "MY_EVENTS" -> {
                parentFragmentManager.beginTransaction().replace(R.id.mainContainer, MyEventsFragment()).commit()
            }
            "PROFILE" -> {
                parentFragmentManager.beginTransaction().replace(R.id.mainContainer, ProfileFragment()).commit()
            }
            else -> {
                parentFragmentManager.beginTransaction().replace(R.id.mainContainer, HomeFragment()).commit()
            }
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_DESTINATION = "destination"
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_EVENT_TITLE = "event_title"
        fun newInstance(destination: String?, eventId: Int = -1, eventTitle: String? = null): LoginFragment {
            return LoginFragment().apply {
                    arguments = Bundle().apply {
                            putString(ARG_DESTINATION, destination)
                            putInt(ARG_EVENT_ID, eventId)
                            putString(ARG_EVENT_TITLE, eventTitle)
                        }
                }
        }
    }
}