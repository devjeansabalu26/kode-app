package com.kode.app.kode_app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.databinding.FragmentProfileBinding
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.ui.auth.LoginFragment

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null

    private val binding get() = _binding!!

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
        val session = SessionManager(requireContext())
        if (!session.isLoggedIn()) {
            openLogin()
            return
        }
        val userId = session.getUserId()
        if (userId <= 0) {
            session.logout()
            openLogin()
            return
        }
        val userRepository = UserRepository(requireContext())
        val user = userRepository.getUserById(userId)
        if (user == null) {
            session.logout()
            Toast.makeText(requireContext(), "La sesión ya no es válida", Toast.LENGTH_SHORT).show()
            openLogin()
            return
        }
        val tvName = binding.tvProfileName
        val tvEmail = binding.tvProfileEmail
        val tvDni = binding.tvProfileDni
        val tvPhone = binding.tvProfilePhone
        val tvGender = binding.tvProfileGender
        val tvAge = binding.tvProfileAge
        val btnLogout = binding.btnLogout
        tvName.text = user.name
        tvEmail.text = user.email
        tvDni.text = "DNI: ${user.dni}"
        tvPhone.text = "Celular: ${user.phone}"
        tvGender.text = "Género: ${user.gender}"

        tvAge.text =
            "Edad: ${user.age}"
        btnLogout.setOnClickListener {
            session.logout()
            Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()
            openLogin()
        }
    }

    private fun openLogin() {
        parentFragmentManager.beginTransaction().replace(R.id.mainContainer, LoginFragment.newInstance(destination = "PROFILE")).commit()
    }
}