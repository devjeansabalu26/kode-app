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
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.R
import com.kode.app.kode_app.databinding.FragmentRegisterBinding
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.RegistrationRepository
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.core.goToTab
import com.kode.app.kode_app.core.navigateTo
import com.kode.app.kode_app.core.goBack
import com.kode.app.kode_app.ui.events.TicketFragment
import kotlinx.coroutines.launch
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
        binding.btnBack.setOnClickListener { goBack() }
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

        etDni.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val dniActual = etDni.text.toString().trim()
                if (dniActual.isNotEmpty() && dniActual.length < 8) {
                    etDni.error = "Por favor, colocar el campo completo (8 dígitos)"
                    showMessage("Por favor, colocar el campo completo (8 dígitos)")
                }
            }
        }

        etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val phoneActual = etPhone.text.toString().trim()
                if (phoneActual.isNotEmpty() && phoneActual.length < 9) {
                    etPhone.error = "Por favor, colocar el campo completo (9 dígitos)"
                    showMessage("Por favor, colocar el campo completo (9 dígitos)")
                }
            }
        }

        etAge.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val ageText = etAge.text.toString().trim()
                if (ageText.isNotEmpty()) {
                    val age = ageText.toIntOrNull()
                    if (age == null || age < 18) {
                        etAge.error = "Debes ser mayor de 18 años"
                        showMessage("Debes ser mayor de 18 años")
                    }
                }
            }
        }

        etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val email = etEmail.text.toString().trim().lowercase()
                if (email.isNotEmpty()) {
                    val tieneFormatoCorrecto = Patterns.EMAIL_ADDRESS.matcher(email).matches()

                    val esCorreoDeConfianza = email.endsWith("@gmail.com") ||
                            email.endsWith("@outlook.com") ||
                            email.endsWith("@hotmail.com")

                    if (!tieneFormatoCorrecto || !esCorreoDeConfianza) {
                        etEmail.error = "Usa un correo válido y de confianza (ej. Gmail, Outlook)"
                        showMessage("Usa un correo válido y de confianza (ej. Gmail, Outlook)")
                    }
                }
            }
        }

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
                if (dni.length < 8) {
                    showMessage("Por favor, colocar el campo completo (8 dígitos)")
                    return@setOnClickListener
                    } else if (dni.length > 8) {
                        showMessage("El DNI no puede tener más de 8 dígitos")
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
                btnRegister.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val userRepository = UserRepository()
                        if (userRepository.userExistsByEmail(email)) {
                            showMessage("Este correo ya está registrado")
                            return@launch
                        }
                        if (userRepository.userExistsByDni(dni)) {
                            showMessage("Este DNI ya está registrado")
                            return@launch
                        }
                        val userId =
                            userRepository.registerUser(name = name, dni = dni, phone = phone, gender = gender, age = age, email = email, password = password)
                        if (userId == -1L) {
                            showMessage("No se pudo crear la cuenta")
                            return@launch
                        }
                        SessionManager(requireContext()).createSession(userId = userId, name = name, email = email.lowercase())
                        showMessage("Cuenta creada correctamente")
                        continuePendingAction(RegistrationRepository(), userId)
                    } finally {
                        _binding?.btnRegister?.isEnabled = true
                    }
                }
            }
        tvGoLogin.setOnClickListener {
            navigateTo(
                R.id.loginFragment,
                LoginFragment.args(
                    destination = arguments?.getString(ARG_DESTINATION) ?: "",
                    eventId = arguments?.getInt(ARG_EVENT_ID, -1) ?: -1,
                    eventTitle = arguments?.getString(ARG_EVENT_TITLE)
                )
            )
        }
    }

    private suspend fun continuePendingAction(registrationRepository: RegistrationRepository, userId: Long) {
        when (arguments?.getString(ARG_DESTINATION)) {
            "REGISTER_EVENT" -> {
                val eventId = arguments?.getInt(ARG_EVENT_ID) ?: return
                val eventTitle = arguments?.getString(ARG_EVENT_TITLE) ?: ""
                val qrCode = registrationRepository.registerUserInEvent(eventId = eventId, userId = userId)
                if (qrCode == null) {
                    showMessage("No se pudo registrar en el evento")
                    return
                }
                navigateTo(
                    R.id.ticketFragment,
                    TicketFragment.args(eventId = eventId, eventTitle = eventTitle, qrCode = qrCode),
                    popUpToId = R.id.eventDetailFragment
                )
            }
            "CREATE_EVENT" -> {
                goToTab(R.id.nav_create_event)
            }
            "MY_EVENTS" -> {
                goToTab(R.id.nav_my_events)
            }
            "PROFILE" -> {
                goToTab(R.id.nav_profile)
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
        fun args(destination: String?, eventId: Int = -1, eventTitle: String? = null): Bundle {
            return Bundle().apply {
                putString(ARG_DESTINATION, destination)
                putInt(ARG_EVENT_ID, eventId)
                putString(ARG_EVENT_TITLE, eventTitle)
            }
        }
    }
}