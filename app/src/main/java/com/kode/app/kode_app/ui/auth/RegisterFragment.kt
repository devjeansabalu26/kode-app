package com.kode.app.kode_app.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.databinding.FragmentRegisterBinding
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.RegistrationRepository
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.ui.events.EventFormFragment
import com.kode.app.kode_app.ui.events.MyEventsFragment
import com.kode.app.kode_app.ui.events.TicketFragment
import com.kode.app.kode_app.ui.profile.ProfileFragment
class RegisterFragment :
    Fragment() {
    private var _binding: FragmentRegisterBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val etName = binding.etName
        val etDni = binding.etDni
        val etPhone = binding.etPhone
        val spGender = binding.spGender
        val etAge = binding.etAge
        val etEmail = binding.etRegisterEmail
        val etPassword = binding.etRegisterPassword
        val btnRegister = binding.btnRegister
        val tvGoLogin = binding.tvGoLogin
        val genders = listOf("Selecciona", "Masculino", "Femenino", "Otro", "Prefiero no indicar")
        val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, genders)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spGender.adapter = genderAdapter
        btnRegister.setOnClickListener {
                val name = etName.text.toString().trim()
                val dni = etDni.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val gender = spGender.selectedItem.toString()
                val ageText = etAge.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()
                if (name.isEmpty() || dni.isEmpty() || phone.isEmpty() || ageText.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    showMessage("Completa todos los campos")
                    return@setOnClickListener
                }
                if (dni.length != 8) {
                    showMessage("El DNI debe tener 8 dígitos")
                    return@setOnClickListener
                }
                if (phone.length != 9) {
                    showMessage("El celular debe tener 9 dígitos")
                    return@setOnClickListener
                }
                if (gender == "Selecciona"
                ) {

                    showMessage(
                        "Selecciona tu género")
                    return@setOnClickListener
                }
                val age = ageText.toIntOrNull()
                if (age == null || age < 1 || age > 120) {
                    showMessage("Ingresa una edad válida")
                    return@setOnClickListener
                }
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    showMessage("Ingresa un correo válido")
                    return@setOnClickListener
                }
                if (password.length < 6) {
                    showMessage("La contraseña debe tener al menos 6 caracteres")
                    return@setOnClickListener
                }
                val userRepository = UserRepository(requireContext())
                if (userRepository.userExistsByEmail(email)) {
                    showMessage("Este correo ya está registrado")
                    return@setOnClickListener
                }
                if (userRepository.userExistsByDni(dni)) {
                    showMessage("Este DNI ya está registrado")
                    return@setOnClickListener
                }
                val userId =
                    userRepository.registerUser(name = name, dni = dni, phone = phone, gender = gender, age = age, email = email, password = password)
                if (userId == -1L) {
                    showMessage("No se pudo crear la cuenta")
                    return@setOnClickListener
                }
                SessionManager(requireContext()).createSession(userId = userId, name = name, email = email.lowercase())
                showMessage("Cuenta creada correctamente")
                continuePendingAction(RegistrationRepository(requireContext()), userId)
            }
        tvGoLogin.setOnClickListener {
                parentFragmentManager.beginTransaction().replace(
                    R.id.mainContainer,
                    LoginFragment.newInstance(
                        destination = arguments?.getString(ARG_DESTINATION),
                        eventId = arguments?.getInt(ARG_EVENT_ID) ?: -1,
                        eventTitle = arguments?.getString(ARG_EVENT_TITLE)
                    )
                ).addToBackStack(null).commit()
            }
    }

    private fun continuePendingAction(registrationRepository: RegistrationRepository, userId: Long) {
        when (arguments?.getString(ARG_DESTINATION)) {
            "REGISTER_EVENT" -> {
                val eventId = arguments?.getInt(ARG_EVENT_ID) ?: return
                val eventTitle = arguments?.getString(ARG_EVENT_TITLE) ?: ""
                val qrCode = registrationRepository.registerUserInEvent(eventId = eventId, userId = userId)
                if (qrCode == null) {
                    showMessage("No se pudo registrar en el evento")
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
        fun newInstance(destination: String?, eventId: Int = -1, eventTitle: String? = null): RegisterFragment {
            return RegisterFragment().apply {
                    arguments = Bundle().apply {
                            putString(ARG_DESTINATION, destination)
                            putInt(ARG_EVENT_ID, eventId)
                            putString(ARG_EVENT_TITLE, eventTitle)
                        }
                }
        }
    }
}