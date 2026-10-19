package com.kode.app.kode_app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.R
import com.kode.app.kode_app.databinding.FragmentProfileBinding
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.core.navigateTo
import com.kode.app.kode_app.core.goBack
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.ui.auth.LoginFragment
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri


class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Botón para regresar
        binding.btnBack.setOnClickListener { goBack() }

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

        val userRepository = UserRepository()
        val eventRepository = EventRepository()

        // 2. Cargar y mostrar datos reales del usuario (Nombre, Email, Iniciales)
        userRepository.observeUser(userId).observe(viewLifecycleOwner) { user ->
            if (user == null) {
                session.logout()
                Toast.makeText(requireContext(), "La sesión ya no es válida", Toast.LENGTH_SHORT)
                    .show()
                openLogin()
                return@observe
            }
            binding.tvProfileName.text = user.name
            binding.tvProfileEmail.text = user.email

            // Obtener iniciales para los avatares circulares
            val initials = user.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2)
                .joinToString("").uppercase()
            val finalInitials = initials.ifEmpty { "K" }
            binding.tvProfileAvatar.text = finalInitials
            binding.tvAvatarHeader.text = finalInitials.firstOrNull()?.toString() ?: "K"
        }

        // 3. Cargar contadores reales desde Firestore (Asistidos y Creados)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val createdEvents = eventRepository.getEventsCreatedByUser(userId)
                val registeredEvents = eventRepository.getEventsRegisteredByUser(userId)

                binding.tvCreatedCount.text = createdEvents.size.toString()
                binding.tvAttendedCount.text = registeredEvents.size.toString()
            } catch (e: Exception) {
                // Manejo silencioso ante errores de conexión
            }
        }

        // 4. Habilitar la navegación a Editar Perfil
        binding.btnEditProfile.setOnClickListener {
            navigateTo(R.id.editProfileFragment)
        }

        // 5. Cerrar sesión
        binding.btnLogout.setOnClickListener {
            session.logout()
            Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()
            openLogin()
        }

        binding.btnSoporte.setOnClickListener {
            abrirSoporteCorreo()
        }
    }

    private fun abrirSoporteCorreo() {
        val correo = "soporte.kode@gmail.com"
        val asunto = "Soporte KODE"
        val mensaje = "Hola, necesito ayuda con la app KODE.\n\n"

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(correo))
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            putExtra(Intent.EXTRA_TEXT, mensaje)
        }

        try {
            startActivity(Intent.createChooser(intent, "Enviar correo con"))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "No hay una app de correo instalada",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun openLogin() {
        navigateTo(
            R.id.loginFragment,
            LoginFragment.args(destination = "PROFILE"),
            replaceCurrent = true
        )
    }

}