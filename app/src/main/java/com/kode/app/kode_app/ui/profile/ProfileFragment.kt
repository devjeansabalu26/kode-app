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
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.ui.auth.LoginFragment

class ProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_profile,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view,
            savedInstanceState
        )

        val session =
            SessionManager(
                requireContext()
            )

        if (!session.isLoggedIn()) {

            openLogin()

            return
        }

        val userId =
            session.getUserId()

        if (userId <= 0) {

            session.logout()

            openLogin()

            return
        }

        val userRepository =
            UserRepository(
                requireContext()
            )

        val user =
            userRepository.getUserById(
                userId
            )

        if (user == null) {

            session.logout()

            Toast.makeText(
                requireContext(),
                "La sesión ya no es válida",
                Toast.LENGTH_SHORT
            ).show()

            openLogin()

            return
        }

        val tvName =
            view.findViewById<TextView>(
                R.id.tvProfileName
            )

        val tvEmail =
            view.findViewById<TextView>(
                R.id.tvProfileEmail
            )

        val tvDni =
            view.findViewById<TextView>(
                R.id.tvProfileDni
            )

        val tvPhone =
            view.findViewById<TextView>(
                R.id.tvProfilePhone
            )

        val tvGender =
            view.findViewById<TextView>(
                R.id.tvProfileGender
            )

        val tvAge =
            view.findViewById<TextView>(
                R.id.tvProfileAge
            )

        val btnLogout =
            view.findViewById<Button>(
                R.id.btnLogout
            )

        tvName.text =
            user.name

        tvEmail.text =
            user.email

        tvDni.text =
            "DNI: ${user.dni}"

        tvPhone.text =
            "Celular: ${user.phone}"

        tvGender.text =
            "Género: ${user.gender}"

        tvAge.text =
            "Edad: ${user.age}"

        btnLogout.setOnClickListener {

            session.logout()

            Toast.makeText(
                requireContext(),
                "Sesión cerrada",
                Toast.LENGTH_SHORT
            ).show()

            openLogin()
        }
    }

    private fun openLogin() {

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.mainContainer,

                LoginFragment.newInstance(
                    destination = "PROFILE"
                )
            )
            .commit()
    }
}