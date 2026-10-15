package com.kode.app.kode_app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.RegistrationRepository
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.databinding.FragmentLoginBinding
import com.kode.app.kode_app.core.goToTab
import com.kode.app.kode_app.core.navigateTo
import com.kode.app.kode_app.ui.events.TicketFragment
import androidx.navigation.fragment.findNavController

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val destination = arguments?.getString(ARG_DESTINATION) ?: ""
        val eventId = arguments?.getInt(ARG_EVENT_ID, -1) ?: -1
        val eventTitle = arguments?.getString(ARG_EVENT_TITLE)


        crearUsuarioDePrueba()


        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userRepository = UserRepository(requireContext())

            val user = userRepository.login(email, password)

            if (user != null) {

                SessionManager(requireContext()).createSession(
                    userId = user.id,
                    name = user.name,
                    email = user.email
                )
                Toast.makeText(requireContext(), "¡Bienvenido, ${user.name}!", Toast.LENGTH_SHORT).show()

                if (destination == "REGISTER_EVENT" && eventId > 0) {
                    val qrCode = RegistrationRepository(requireContext()).registerUserInEvent(eventId, user.id)
                    if (qrCode != null) {
                        navigateTo(
                            R.id.ticketFragment,
                            TicketFragment.args(eventId = eventId, eventTitle = eventTitle, qrCode = qrCode),
                            popUpToId = R.id.eventDetailFragment
                        )
                    } else {
                        Toast.makeText(requireContext(), "No se pudo completar la inscripción", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                    return@setOnClickListener
                }

                goToTab(R.id.nav_my_events)
            } else {
                Toast.makeText(requireContext(), "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
            }
        }


        binding.tvRegister.setOnClickListener {
            navigateTo(R.id.registerFragment, RegisterFragment.args(destination, eventId, eventTitle))
        }
    }

    private fun crearUsuarioDePrueba() {
        val userRepository = UserRepository(requireContext())
        val emailPrueba = "Kade@gmail.com"

        if (!userRepository.userExistsByEmail(emailPrueba)) {
            userRepository.registerUser(
                name = "Kade Lopez",
                dni = "12345678",
                phone = "987654321",
                gender = "Masculino",
                age = 25,
                email = emailPrueba,
                password = "kade123"
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_DESTINATION = "destination"
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_EVENT_TITLE = "event_title"

        fun args(destination: String = "", eventId: Int = -1, eventTitle: String? = null): Bundle {
            return Bundle().apply {
                putString(ARG_DESTINATION, destination)
                putInt(ARG_EVENT_ID, eventId)
                putString(ARG_EVENT_TITLE, eventTitle)
            }
        }
    }
}