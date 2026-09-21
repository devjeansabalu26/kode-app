package com.kode.app.kode_app.ui.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.core.AppError
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.repository.EventRepository
import com.kode.app.kode_app.databinding.FragmentEventFormBinding
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.home.HomeFragment
import com.kode.app.kode_app.ui.navigateTo
import com.kode.app.kode_app.ui.showError
import com.kode.app.kode_app.ui.showMessage
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

/** Formulario de evento. Sin argumentos crea un evento nuevo; con `newEditInstance(id)` carga y edita ese evento. */
class EventFormFragment : Fragment() {
    private var _binding: FragmentEventFormBinding? = null

    private val binding get() = _binding!!

    private val session = SessionManager()

    private val eventRepository = EventRepository()

    private val selectedDateTime = Calendar.getInstance()

    private var dateSelected = false

    private var timeSelected = false

    /** > 0 cuando estamos editando un evento existente. */
    private val editingEventId get() = arguments?.getLong(ARG_EVENT_ID, -1L) ?: -1L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!session.isLoggedIn()) {
            openLogin()
            return
        }
        binding.etEventDate.setOnClickListener { showDatePicker() }
        binding.etEventTime.setOnClickListener { showTimePicker() }
        binding.btnCreateEvent.setOnClickListener { submit() }
        binding.btnCancelEvent.setOnClickListener { navigateTo(HomeFragment()) }
        if (editingEventId > 0) loadEventForEditing(editingEventId)
    }

    private fun loadEventForEditing(eventId: Long) {
        binding.btnCreateEvent.text = "Guardar cambios"
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = eventRepository.getEventById(eventId)) {
                is AppResult.Failure -> {
                    showError(result.error)
                    navigateTo(HomeFragment())
                }
                is AppResult.Success -> {
                    if (result.value.creatorId != session.getUserId()) {
                        showError(AppError.FORBIDDEN)
                        navigateTo(HomeFragment())
                    } else {
                        fillForm(result.value)
                    }
                }
            }
        }
    }

    private fun fillForm(event: Event) {
        binding.etEventTitle.setText(event.title)
        binding.etEventDescription.setText(event.description)
        binding.etEventCategory.setText(event.category)
        binding.etEventLocation.setText(event.location)
        binding.etEventCapacity.setText(event.capacity.toString())
        selectedDateTime.timeInMillis = event.date.toEpochMilli()
        binding.etEventDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDateTime.time))
        binding.etEventTime.setText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedDateTime.time))
        dateSelected = true
        timeSelected = true
    }

    private fun submit() {
        val title = binding.etEventTitle.text.toString().trim()
        val description = binding.etEventDescription.text.toString().trim()
        val category = binding.etEventCategory.text.toString().trim()
        val location = binding.etEventLocation.text.toString().trim()
        val capacityText = binding.etEventCapacity.text.toString().trim()
        if (title.isEmpty()) {
            showMessage("Ingresa el nombre del evento")
            return
        }
        if (description.isEmpty()) {
            showMessage("Ingresa una descripción")
            return
        }
        if (category.isEmpty()) {
            showMessage("Ingresa una categoría")
            return
        }
        if (location.isEmpty()) {
            showMessage("Ingresa la ubicación")
            return
        }
        if (!dateSelected) {
            showMessage("Selecciona la fecha")
            return
        }
        if (!timeSelected) {
            showMessage("Selecciona la hora")
            return
        }
        val capacity = capacityText.toIntOrNull()
        if (capacity == null || capacity <= 0) {
            showMessage("Ingresa una capacidad válida")
            return
        }
        if (selectedDateTime.timeInMillis <= System.currentTimeMillis()) {
            showMessage("La fecha del evento debe ser futura")
            return
        }
        val date = Instant.ofEpochMilli(selectedDateTime.timeInMillis)
        val editingId = editingEventId
        binding.btnCreateEvent.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (editingId > 0) {
                eventRepository.updateEvent(editingId, title, description, date, location, category, capacity)
            } else {
                eventRepository.createEvent(title, description, date, location, category, capacity)
            }
            when (result) {
                is AppResult.Success -> {
                    showMessage(if (editingId > 0) "Evento actualizado correctamente" else "Evento publicado correctamente")
                    navigateTo(HomeFragment())
                }
                is AppResult.Failure -> {
                    showError(result.error)
                    if (result.error == AppError.NOT_AUTHENTICATED) openLogin()
                    _binding?.btnCreateEvent?.isEnabled = true
                }
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDateTime.set(Calendar.YEAR, year)
                selectedDateTime.set(Calendar.MONTH, month)
                selectedDateTime.set(Calendar.DAY_OF_MONTH, day)
                binding.etEventDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDateTime.time))
                dateSelected = true
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                selectedDateTime.set(Calendar.HOUR_OF_DAY, hour)
                selectedDateTime.set(Calendar.MINUTE, minute)
                selectedDateTime.set(Calendar.SECOND, 0)
                selectedDateTime.set(Calendar.MILLISECOND, 0)
                binding.etEventTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
                timeSelected = true
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun openLogin() {
        navigateTo(LoginFragment.newInstance(destination = "CREATE_EVENT"))
    }

    companion object {
        private const val ARG_EVENT_ID = "event_id"

        fun newEditInstance(eventId: Long): EventFormFragment {
            return EventFormFragment().apply {
                arguments = Bundle().apply { putLong(ARG_EVENT_ID, eventId) }
            }
        }
    }
}
