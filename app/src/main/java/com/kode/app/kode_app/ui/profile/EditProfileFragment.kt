package com.kode.app.kode_app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.databinding.FragmentEditProfileBinding
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.core.goBack
import com.kode.app.kode_app.data.UserRepository
import kotlinx.coroutines.launch

class EditProfileFragment : Fragment() {
    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBackEdit.setOnClickListener { goBack() }

        val session = SessionManager(requireContext())
        val userId = session.getUserId()
        val userRepository = UserRepository()

        
        val genders = arrayOf("Masculino", "Femenino", "Prefiero no decirlo")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, genders)
        binding.spEditGender.adapter = adapter


        lifecycleScope.launch {
            val user = userRepository.getUserById(userId)
            if (user != null) {
                binding.etEditName.setText(user.name)
                binding.etEditDni.setText(user.dni) // Solo lectura
                binding.etEditPhone.setText(user.phone)
                binding.etEditAge.setText(user.age.toString())

                val genderIndex = genders.indexOf(user.gender)
                if (genderIndex >= 0) binding.spEditGender.setSelection(genderIndex)
            }
        }


        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etEditName.text.toString().trim()
            val phone = binding.etEditPhone.text.toString().trim()
            val gender = binding.spEditGender.selectedItem.toString()
            val age = binding.etEditAge.text.toString().toIntOrNull() ?: 0

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(requireContext(), "Por favor completa los campos obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val success = userRepository.updateUser(userId, name, phone, gender, age)
                if (success) {
                    Toast.makeText(requireContext(), "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                    goBack()
                } else {
                    Toast.makeText(requireContext(), "Error al actualizar el perfil", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}