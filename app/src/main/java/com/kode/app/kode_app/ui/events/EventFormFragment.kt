package com.kode.app.kode_app.ui.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.kode.app.kode_app.R
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.databinding.FragmentEventFormBinding
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.home.HomeFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EventFormFragment : Fragment() {
    private var _binding: FragmentEventFormBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionManager
    private lateinit var userRepository: UserRepository
    private lateinit var eventRepository: EventRepository

    private val selectedDateTime = Calendar.getInstance()
    private var dateSelected = false
    private var timeSelected = false
    private var selectedEndTime = ""

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
        session = SessionManager(requireContext())
        userRepository = UserRepository(requireContext())
        eventRepository = EventRepository(requireContext())

        if (!session.isLoggedIn()) {
            openLogin()
            return
        }
        val userId = session.getUserId()
        if (userId <= 0 || userRepository.getUserById(userId) == null) {
            session.logout()
            openLogin()
            return
        }

        binding.etEventDate.setOnClickListener { showDatePicker(binding.etEventDate) }
        binding.etStartTime.setOnClickListener { showTimePicker(binding.etStartTime, isStart = true) }
        binding.etEndTime.setOnClickListener { showTimePicker(binding.etEndTime, isStart = false) }

        binding.btnBack.setOnClickListener { navigateToHome() }
        binding.btnCancel.setOnClickListener { navigateToHome() }
        binding.btnSaveDraft.setOnClickListener { showMessage("Borrador guardado") }

        binding.btnPublish.setOnClickListener {
            val title = binding.etEventTitle.text.toString().trim()
            val description = binding.etEventDescription.text.toString().trim()
            val location = binding.etLocation.text.toString().trim()
            val capacityText = binding.etCapacity.text.toString().trim()
            val accessCode = binding.etAccessCode.text.toString().trim()

            // Obtener Categoría seleccionada de Chips
            val selectedChipId = binding.chipGroupCategory.checkedChipId
            val category = if (selectedChipId != View.NO_ID) {
                binding.root.findViewById<Chip>(selectedChipId).text.toString().replace("⚡", "").trim()
            } else "Tecnología"


            val eventType = when (binding.toggleEventType.checkedButtonId) {
                R.id.btnVirtual -> "Virtual"
                R.id.btnHibrido -> "Híbrido"
                else -> "Presencial"
            }


            val isPrivate = binding.togglePrivacy.checkedButtonId == R.id.btnPrivado

            if (title.isEmpty()) { showMessage("Ingresa el nombre del evento"); return@setOnClickListener }
            if (description.isEmpty()) { showMessage("Ingresa una descripción"); return@setOnClickListener }
            if (location.isEmpty()) { showMessage("Ingresa la ubicación"); return@setOnClickListener }
            if (!dateSelected) { showMessage("Selecciona la fecha"); return@setOnClickListener }
            if (!timeSelected) { showMessage("Selecciona la hora de inicio"); return@setOnClickListener }

            val capacity = capacityText.toIntOrNull()
            if (capacity == null || capacity <= 0) {
                showMessage("Ingresa una capacidad válida")
                return@setOnClickListener
            }

            if (selectedDateTime.timeInMillis <= System.currentTimeMillis()) {
                showMessage("La fecha del evento debe ser futura")
                return@setOnClickListener
            }

            val creatorId = session.getUserId()
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val databaseDate = formatter.format(selectedDateTime.time)

            val eventId = eventRepository.createEvent(
                creatorId = creatorId,
                title = title,
                description = description,
                date = databaseDate,
                location = location,
                category = category,
                capacity = capacity,
                endTime = selectedEndTime,
                eventType = eventType,
                isPrivate = isPrivate,
                requireDni = binding.cbRequireDni.isChecked,
                isUntransferable = binding.cbUntransferable.isChecked,
                accessCode = accessCode
            )

            if (eventId == -1L) {
                showMessage("No se pudo crear el evento")
                return@setOnClickListener
            }

            showMessage("Evento publicado correctamente")
            navigateToHome()
        }
    }

    private fun showDatePicker(etDate: EditText) {
        val calendar = Calendar.getInstance()
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDateTime.set(Calendar.YEAR, year)
                selectedDateTime.set(Calendar.MONTH, month)
                selectedDateTime.set(Calendar.DAY_OF_MONTH, day)
                val displayFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                etDate.setText(displayFormatter.format(selectedDateTime.time))
                dateSelected = true
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    private fun showTimePicker(etTime: EditText, isStart: Boolean) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                val formattedTime = String.format(Locale.getDefault(), "%02d:%02d hrs", hour, minute)
                etTime.setText(formattedTime)
                if (isStart) {
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, hour)
                    selectedDateTime.set(Calendar.MINUTE, minute)
                    selectedDateTime.set(Calendar.SECOND, 0)
                    selectedDateTime.set(Calendar.MILLISECOND, 0)
                    timeSelected = true
                } else {
                    selectedEndTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun navigateToHome() {
        parentFragmentManager.beginTransaction().replace(R.id.mainContainer, HomeFragment()).commit()
    }

    private fun openLogin() {
        parentFragmentManager.beginTransaction().replace(R.id.mainContainer, LoginFragment.newInstance(destination = "CREATE_EVENT")).commit()
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}