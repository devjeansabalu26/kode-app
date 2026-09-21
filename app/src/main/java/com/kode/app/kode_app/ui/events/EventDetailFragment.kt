package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.databinding.FragmentEventDetailBinding
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.data.RegistrationRepository
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.ui.auth.LoginFragment

class EventDetailFragment : Fragment() {
    private var _binding: FragmentEventDetailBinding? = null

    private val binding get() = _binding!!

    private lateinit var eventRepository: EventRepository

    private lateinit var registrationRepository: RegistrationRepository
    private lateinit var session: SessionManager

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
        eventRepository = EventRepository(requireContext())
        registrationRepository = RegistrationRepository(requireContext())
        session = SessionManager(requireContext())
        val eventId = arguments?.getInt(ARG_ID, -1) ?: -1
        if (eventId <= 0) {
            showMessage("No se pudo identificar el evento")
            return
        }
        val event = eventRepository.getEventById(eventId)
        if (event == null) {
            showMessage("El evento no existe")
            return
        }
        val tvCategory = binding.tvDetailCategory
        val tvTitle = binding.tvDetailTitle
        val tvDescription = binding.tvDetailDescription
        val tvDate = binding.tvDetailDate
        val tvLocation = binding.tvDetailLocation
        val btnRegister = binding.btnRegisterEvent
        val creatorSection = binding.creatorManagementSection
        val tvRegistered = binding.tvRegisteredProgress
        val progressRegistered = binding.progressRegistered
        val tvAttended = binding.tvAttendedProgress
        val progressAttended = binding.progressAttended
        val btnEdit = binding.btnEditEvent
        tvCategory.text = event.category
        tvTitle.text = event.title
        tvDescription.text = event.description
        tvDate.text = event.date
        tvLocation.text = event.location
        val currentUserId = if (session.isLoggedIn()) {
                session.getUserId()
            } else {
                -1L
            }
        val isCreator = currentUserId > 0 && event.creatorId != null && event.creatorId == currentUserId
        if (isCreator) {
            showCreatorMode(
                event = event,
                btnRegister = btnRegister,
                creatorSection = creatorSection,
                tvRegistered = tvRegistered,
                progressRegistered = progressRegistered,
                tvAttended = tvAttended,
                progressAttended = progressAttended,
                btnEdit = btnEdit
            )
        } else {
            showParticipantMode(event = event, btnRegister = btnRegister, creatorSection = creatorSection)
        }
    }

    private fun showCreatorMode(
        event: Event,
        btnRegister: Button,
        creatorSection: LinearLayout,
        tvRegistered: TextView,
        progressRegistered: ProgressBar,
        tvAttended: TextView,
        progressAttended: ProgressBar,
        btnEdit: Button
    ) {
        btnRegister.visibility = View.GONE
        creatorSection.visibility = View.VISIBLE
        tvRegistered.text = "Registrados: ${event.registeredCount} / ${event.capacity}"
        val registeredPercent = calculatePercentage(value = event.registeredCount, maximum = event.capacity)
        progressRegistered.max = 100
        progressRegistered.progress = registeredPercent
        tvAttended.text = "Asistieron: ${event.attendedCount} / ${event.capacity}"
        val attendedPercent = calculatePercentage(value = event.attendedCount, maximum = event.capacity)
        progressAttended.max = 100
        progressAttended.progress = attendedPercent
        btnEdit.setOnClickListener {
            parentFragmentManager.beginTransaction().replace(R.id.mainContainer, EventFormFragment.newEditInstance(event.id)).addToBackStack(null)
                .commit()
        }
    }

    private fun showParticipantMode(event: Event, btnRegister: Button, creatorSection: LinearLayout) {
        creatorSection.visibility = View.GONE
        btnRegister.visibility = View.VISIBLE
        if (event.registeredCount >= event.capacity) {
            btnRegister.text = "Aforo completo"
            btnRegister.isEnabled = false
            return
        }
        if (session.isLoggedIn() && registrationRepository.isUserRegisteredInEvent(eventId = event.id, userId = session.getUserId())) {
            btnRegister.text = "Ver mi pase"
        } else {
            btnRegister.text = "Quiero asistir"
        }
        btnRegister.isEnabled = true
        btnRegister.setOnClickListener {
            registerOrLogin(event)
        }
    }

    private fun registerOrLogin(event: Event) {
        if (!session.isLoggedIn()) {
            openLogin(event)
            return
        }
        val userId = session.getUserId()
        if (userId <= 0) {
            session.logout()
            showMessage("Tu sesión no es válida. Inicia sesión nuevamente.")
            openLogin(event)
            return
        }
        if (event.creatorId == userId) {
            showMessage("Eres el creador de este evento")
            return
        }
        val updatedEvent = eventRepository.getEventById(event.id)
        if (updatedEvent == null) {
            showMessage("El evento ya no está disponible")
            return
        }
        if (updatedEvent.registeredCount >= updatedEvent.capacity) {
            showMessage("El evento alcanzó su capacidad máxima")
            return
        }
        val qrCode = registrationRepository.registerUserInEvent(eventId = event.id, userId = userId)
        if (qrCode == null) {
            showMessage("No se pudo realizar la inscripción")
            return
        }
        openTicket(eventId = event.id, eventTitle = event.title, qrCode = qrCode)
    }

    private fun openLogin(event: Event) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, LoginFragment.newInstance(destination = "REGISTER_EVENT", eventId = event.id, eventTitle = event.title))
            .addToBackStack(null).commit()
    }

    private fun openTicket(eventId: Int, eventTitle: String, qrCode: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, TicketFragment.newInstance(eventId = eventId, eventTitle = eventTitle, qrCode = qrCode))
            .addToBackStack(null).commit()
    }

    private fun calculatePercentage(value: Int, maximum: Int): Int {
        if (maximum <= 0) {
            return 0
        }
        return (value * 100 / maximum).coerceIn(0, 100)
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_ID = "id"
        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_DATE = "date"
        private const val ARG_LOCATION = "location"
        private const val ARG_CATEGORY = "category"
        private const val ARG_CREATOR_ID = "creator_id"
        private const val ARG_CAPACITY = "capacity"
        private const val ARG_REGISTERED = "registered_count"
        private const val ARG_ATTENDED = "attended_count"
        private const val ARG_EVENT_ID = "event_id"
        fun newEditInstance(eventId: Int): EventFormFragment {
            return EventFormFragment().apply {
                    arguments = Bundle().apply {
                            putInt(ARG_EVENT_ID, eventId)
                        }
                }
        }
        fun newInstance(event: Event): EventDetailFragment {
            return EventDetailFragment().apply {
                    arguments = Bundle().apply {
                            putInt(ARG_ID, event.id)
                            putString(ARG_TITLE, event.title)
                            putString(ARG_DESCRIPTION, event.description)
                            putString(ARG_DATE, event.date)
                            putString(ARG_LOCATION, event.location)
                            putString(ARG_CATEGORY, event.category)
                            event.creatorId?.let {
                                putLong(ARG_CREATOR_ID, it)
                            }
                            putInt(ARG_CAPACITY, event.capacity)
                            putInt(ARG_REGISTERED, event.registeredCount)
                            putInt(ARG_ATTENDED, event.attendedCount)
                        }
                }
        }
    }
}