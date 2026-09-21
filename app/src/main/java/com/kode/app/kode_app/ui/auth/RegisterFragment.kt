package com.kode.app.kode_app.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.data.remote.SignUpOutcome
import com.kode.app.kode_app.data.repository.AuthRepository
import com.kode.app.kode_app.data.repository.RegisterForm
import com.kode.app.kode_app.databinding.FragmentRegisterBinding
import com.kode.app.kode_app.ui.navigateTo
import com.kode.app.kode_app.ui.showError
import com.kode.app.kode_app.ui.showMessage
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null

    private val binding get() = _binding!!

    private val authRepository = AuthRepository()

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
        val genders = listOf("Selecciona", "Masculino", "Femenino", "Otro", "Prefiero no indicar")
        val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, genders)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spGender.adapter = genderAdapter
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val dni = binding.etDni.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val gender = binding.spGender.selectedItem.toString()
            val ageText = binding.etAge.text.toString().trim()
            val email = binding.etRegisterEmail.text.toString().trim()
            val password = binding.etRegisterPassword.text.toString()
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
            if (gender == "Selecciona") {
                showMessage("Selecciona tu género")
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
            binding.btnRegister.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = authRepository.register(RegisterForm(name, dni, phone, gender, age, email, password))) {
                    is AppResult.Failure -> {
                        showError(result.error)
                        binding.btnRegister.isEnabled = true
                    }
                    is AppResult.Success -> when (result.value) {
                        SignUpOutcome.LOGGED_IN -> {
                            showMessage("Cuenta creada correctamente")
                            continuePendingAction()
                        }
                        else -> {
                            showMessage("Cuenta creada. Confirma tu correo y luego inicia sesión")
                            navigateTo(LoginFragment.newInstance(arguments?.getString(ARG_DESTINATION), arguments?.getLong(ARG_EVENT_ID, -1L) ?: -1L, arguments?.getString(ARG_EVENT_TITLE)))
                        }
                    }
                }
            }
        }
        binding.tvGoLogin.setOnClickListener {
            navigateTo(
                LoginFragment.newInstance(
                    destination = arguments?.getString(ARG_DESTINATION),
                    eventId = arguments?.getLong(ARG_EVENT_ID, -1L) ?: -1L,
                    eventTitle = arguments?.getString(ARG_EVENT_TITLE)
                ),
                addToBackStack = true
            )
        }
    }

    companion object {
        fun newInstance(destination: String?, eventId: Long = -1L, eventTitle: String? = null): RegisterFragment {
            return RegisterFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESTINATION, destination)
                    putLong(ARG_EVENT_ID, eventId)
                    putString(ARG_EVENT_TITLE, eventTitle)
                }
            }
        }
    }
}
