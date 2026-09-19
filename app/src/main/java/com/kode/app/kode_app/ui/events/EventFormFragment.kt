package com.kode.app.kode_app.ui.events

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.home.HomeFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EventFormFragment : Fragment() {

    private lateinit var session:
            SessionManager

    private lateinit var userRepository:
            UserRepository

    private lateinit var eventRepository:
            EventRepository

    private val selectedDateTime =
        Calendar.getInstance()

    private var dateSelected =
        false

    private var timeSelected =
        false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_event_form,
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

        session =
            SessionManager(
                requireContext()
            )

        userRepository =
            UserRepository(
                requireContext()
            )

        eventRepository =
            EventRepository(
                requireContext()
            )

        if (!session.isLoggedIn()) {

            openLogin()

            return
        }

        val userId =
            session.getUserId()

        if (
            userId <= 0 ||
            userRepository.getUserById(userId) == null
        ) {

            session.logout()

            openLogin()

            return
        }

        val etTitle =
            view.findViewById<EditText>(
                R.id.etEventTitle
            )

        val etDescription =
            view.findViewById<EditText>(
                R.id.etEventDescription
            )

        val etCategory =
            view.findViewById<EditText>(
                R.id.etEventCategory
            )

        val etLocation =
            view.findViewById<EditText>(
                R.id.etEventLocation
            )

        val etDate =
            view.findViewById<EditText>(
                R.id.etEventDate
            )

        val etTime =
            view.findViewById<EditText>(
                R.id.etEventTime
            )

        val etCapacity =
            view.findViewById<EditText>(
                R.id.etEventCapacity
            )

        val btnCreate =
            view.findViewById<Button>(
                R.id.btnCreateEvent
            )

        val btnCancel =
            view.findViewById<Button>(
                R.id.btnCancelEvent
            )

        etDate.setOnClickListener {

            showDatePicker(
                etDate
            )
        }
//holaa
        etTime.setOnClickListener {

            showTimePicker(
                etTime
            )
        }

        btnCreate.setOnClickListener {

            val title =
                etTitle
                    .text
                    .toString()
                    .trim()

            val description =
                etDescription
                    .text
                    .toString()
                    .trim()

            val category =
                etCategory
                    .text
                    .toString()
                    .trim()

            val location =
                etLocation
                    .text
                    .toString()
                    .trim()

            val capacityText =
                etCapacity
                    .text
                    .toString()
                    .trim()

            if (title.isEmpty()) {

                showMessage(
                    "Ingresa el nombre del evento"
                )

                return@setOnClickListener
            }

            if (description.isEmpty()) {

                showMessage(
                    "Ingresa una descripción"
                )

                return@setOnClickListener
            }

            if (category.isEmpty()) {

                showMessage(
                    "Ingresa una categoría"
                )

                return@setOnClickListener
            }

            if (location.isEmpty()) {

                showMessage(
                    "Ingresa la ubicación"
                )

                return@setOnClickListener
            }

            if (!dateSelected) {

                showMessage(
                    "Selecciona la fecha"
                )

                return@setOnClickListener
            }

            if (!timeSelected) {

                showMessage(
                    "Selecciona la hora"
                )

                return@setOnClickListener
            }

            val capacity =
                capacityText.toIntOrNull()

            if (
                capacity == null ||
                capacity <= 0
            ) {

                showMessage(
                    "Ingresa una capacidad válida"
                )

                return@setOnClickListener
            }

            if (
                selectedDateTime.timeInMillis <=
                System.currentTimeMillis()
            ) {

                showMessage(
                    "La fecha del evento debe ser futura"
                )

                return@setOnClickListener
            }

            val creatorId =
                session.getUserId()

            val formatter =
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                )

            val databaseDate =
                formatter.format(
                    selectedDateTime.time
                )

            val eventId =
                eventRepository.createEvent(

                    creatorId =
                        creatorId,

                    title =
                        title,

                    description =
                        description,

                    date =
                        databaseDate,

                    location =
                        location,

                    category =
                        category,

                    capacity =
                        capacity
                )

            if (
                eventId == -1L
            ) {

                showMessage(
                    "No se pudo crear el evento"
                )

                return@setOnClickListener
            }

            showMessage(
                "Evento publicado correctamente"
            )

            parentFragmentManager
                .beginTransaction()
                .replace(
                    R.id.mainContainer,
                    HomeFragment()
                )
                .commit()
        }

        btnCancel.setOnClickListener {

            parentFragmentManager
                .beginTransaction()
                .replace(
                    R.id.mainContainer,
                    HomeFragment()
                )
                .commit()
        }
    }

    private fun showDatePicker(
        etDate: EditText
    ) {

        val calendar =
            Calendar.getInstance()

        val dialog =
            DatePickerDialog(
                requireContext(),

                { _, year, month, day ->

                    selectedDateTime.set(
                        Calendar.YEAR,
                        year
                    )

                    selectedDateTime.set(
                        Calendar.MONTH,
                        month
                    )

                    selectedDateTime.set(
                        Calendar.DAY_OF_MONTH,
                        day
                    )

                    val displayFormatter =
                        SimpleDateFormat(
                            "dd/MM/yyyy",
                            Locale.getDefault()
                        )

                    etDate.setText(
                        displayFormatter.format(
                            selectedDateTime.time
                        )
                    )

                    dateSelected =
                        true
                },

                calendar.get(
                    Calendar.YEAR
                ),

                calendar.get(
                    Calendar.MONTH
                ),

                calendar.get(
                    Calendar.DAY_OF_MONTH
                )
            )

        dialog.datePicker.minDate =
            System.currentTimeMillis() -
                    1000

        dialog.show()
    }

    private fun showTimePicker(
        etTime: EditText
    ) {

        val calendar =
            Calendar.getInstance()

        TimePickerDialog(
            requireContext(),

            { _, hour, minute ->

                selectedDateTime.set(
                    Calendar.HOUR_OF_DAY,
                    hour
                )

                selectedDateTime.set(
                    Calendar.MINUTE,
                    minute
                )

                selectedDateTime.set(
                    Calendar.SECOND,
                    0
                )

                selectedDateTime.set(
                    Calendar.MILLISECOND,
                    0
                )

                etTime.setText(
                    String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        hour,
                        minute
                    )
                )

                timeSelected =
                    true
            },

            calendar.get(
                Calendar.HOUR_OF_DAY
            ),

            calendar.get(
                Calendar.MINUTE
            ),

            true

        ).show()
    }

    private fun openLogin() {

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.mainContainer,

                LoginFragment.newInstance(
                    destination =
                        "CREATE_EVENT"
                )
            )
            .commit()
    }

    private fun showMessage(
        message: String
    ) {

        Toast
            .makeText(
                requireContext(),
                message,
                Toast.LENGTH_SHORT
            )
            .show()
    }

    companion object {

        private const val ARG_EVENT_ID =
            "event_id"

        fun newEditInstance(
            eventId: Int
        ): EventFormFragment {

            return EventFormFragment()
                .apply {

                    arguments =
                        Bundle().apply {

                            putInt(
                                ARG_EVENT_ID,
                                eventId
                            )
                        }
                }
        }
    }
}
