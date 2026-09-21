package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.core.AppError
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.core.DateFormats
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.repository.EventRepository
import com.kode.app.kode_app.data.repository.RegistrationRepository
import com.kode.app.kode_app.databinding.FragmentEventDetailBinding
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.navigateTo
import com.kode.app.kode_app.ui.showError
import com.kode.app.kode_app.ui.showMessage
import kotlinx.coroutines.launch

class EventDetailFragment : Fragment() {
    private var _binding: FragmentEventDetailBinding? = null

    private val binding get() = _binding!!

    private val eventRepository = EventRepository()

    private val registrationRepository = RegistrationRepository()

    private val session = SessionManager()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val eventId = arguments?.getLong(ARG_ID, -1L) ?: -1L
        if (eventId <= 0) {
            showMessage("No se pudo identificar el evento")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = eventRepository.getEventById(eventId)) {
                is AppResult.Failure -> showError(result.error)
                is AppResult.Success -> showEvent(result.value)
            }
        }
    }

    private suspend fun showEvent(event: Event) {
        binding.tvDetailCategory.text = event.category
        binding.tvDetailTitle.text = event.title
        binding.tvDetailDescription.text = event.description
        binding.tvDetailDate.text = DateFormats.display(event.date)
        binding.tvDetailLocation.text = event.location
        val userId = session.getUserId()
        if (userId != null && event.creatorId == userId) {
            showCreatorMode(event)
        } else {
            showParticipantMode(event)
        }
    }

    private fun showCreatorMode(event: Event) {
        binding.btnRegisterEvent.visibility = View.GONE
        binding.creatorManagementSection.visibility = View.VISIBLE
        binding.tvRegisteredProgress.text = "Registrados: ${event.registeredCount} / ${event.capacity}"
        binding.progressRegistered.max = 100
        binding.progressRegistered.progress = calculatePercentage(event.registeredCount, event.capacity)
        binding.tvAttendedProgress.text = "Asistieron: ${event.attendedCount} / ${event.capacity}"
        binding.progressAttended.max = 100
        binding.progressAttended.progress = calculatePercentage(event.attendedCount, event.capacity)
        binding.btnEditEvent.setOnClickListener {
            navigateTo(EventFormFragment.newEditInstance(event.id), addToBackStack = true)
        }
    }

    private suspend fun showParticipantMode(event: Event) {
        binding.creatorManagementSection.visibility = View.GONE
        binding.btnRegisterEvent.visibility = View.VISIBLE
        val alreadyRegistered = session.isLoggedIn() && (registrationRepository.isUserRegisteredInEvent(event.id) as? AppResult.Success)?.value == true
        if (!alreadyRegistered && event.registeredCount >= event.capacity) {
            binding.btnRegisterEvent.text = "Aforo completo"
            binding.btnRegisterEvent.isEnabled = false
            return
        }
        binding.btnRegisterEvent.text = if (alreadyRegistered) "Ver mi pase" else "Quiero asistir"
        binding.btnRegisterEvent.isEnabled = true
        binding.btnRegisterEvent.setOnClickListener { registerOrLogin(event) }
    }

    private fun registerOrLogin(event: Event) {
        if (!session.isLoggedIn()) {
            navigateTo(LoginFragment.newInstance(destination = "REGISTER_EVENT", eventId = event.id, eventTitle = event.title), addToBackStack = true)
            return
        }
        binding.btnRegisterEvent.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = registrationRepository.registerUserInEvent(event.id)) {
                is AppResult.Success -> navigateTo(TicketFragment.newInstance(event.id, event.title, result.value), addToBackStack = true)
                is AppResult.Failure -> {
                    showError(result.error)
                    if (result.error == AppError.NOT_AUTHENTICATED) navigateTo(LoginFragment.newInstance("REGISTER_EVENT", event.id, event.title), addToBackStack = true)
                    _binding?.btnRegisterEvent?.isEnabled = true
                }
            }
        }
    }

    private fun calculatePercentage(value: Int, maximum: Int): Int {
        if (maximum <= 0) return 0
        return (value * 100 / maximum).coerceIn(0, 100)
    }

    companion object {
        private const val ARG_ID = "id"

        /** Solo hace falta el id: el detalle vuelve a pedir el evento a Supabase para mostrar datos frescos. */
        fun newInstance(event: Event): EventDetailFragment {
            return EventDetailFragment().apply {
                arguments = Bundle().apply { putLong(ARG_ID, event.id) }
            }
        }
    }
}
