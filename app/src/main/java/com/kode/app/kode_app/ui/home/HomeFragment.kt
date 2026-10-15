package com.kode.app.kode_app.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.kode.app.kode_app.R
import com.kode.app.kode_app.components.EventAdapter
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.core.goToTab
import com.kode.app.kode_app.core.navigateTo
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.databinding.FragmentHomeBinding
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.ui.events.EventDetailFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var eventRepository: EventRepository

    private var allEvents: List<Event> = emptyList()
    private var filteredEvents: List<Event> = emptyList()
    private var currentFilter: String = FILTER_ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eventRepository = EventRepository(requireContext())

        binding.rvEvents.layoutManager = LinearLayoutManager(requireContext())

        loadEvents()
        loadFilters()
        configureSearch()
        configureActionButtons()

        binding.swipeRefreshHome.setOnRefreshListener {
            loadFilters()
            applyFilters()
            binding.swipeRefreshHome.isRefreshing = false
        }
    }

    private fun configureActionButtons() {
        binding.btnCrearEvento.setOnClickListener {
            goToTab(R.id.nav_create_event)
        }

        binding.btnIngresarCodigo.setOnClickListener {
            showEnterCodeDialog()
        }
    }

    private fun showEnterCodeDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Ejemplo: 123456"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            val paddingPx = dpToPx(16)
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ingresar clave de evento")
            .setMessage("Escribe la clave para acceder al evento privado:")
            .setView(input)
            .setPositiveButton("Acceder") { dialog, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) {
                    // Busca directo por código (sea privado o público)
                    val event = eventRepository.getEventByAccessCode(code)
                    if (event != null) {
                        navigateTo(R.id.eventDetailFragment, EventDetailFragment.args(event))
                    } else {
                        Toast.makeText(requireContext(), "Código no válido o evento inexistente", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Ingresa un código de acceso", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun loadEvents() {
        // Carga únicamente eventos públicos
        allEvents = eventRepository.getPublicEvents()
        filteredEvents = allEvents
        showEvents(filteredEvents)
    }

    private fun loadFilters() {
        binding.filtersContainer.removeAllViews()

        addFilterButton(text = "Todos") {
            currentFilter = FILTER_ALL
            applyFilters()
        }
        addFilterButton(text = "Esta semana") {
            currentFilter = FILTER_THIS_WEEK
            applyFilters()
        }

        val categories = eventRepository.getCategories()
        categories.forEach { category ->
            addFilterButton(text = category) {
                currentFilter = category
                applyFilters()
            }
        }
    }

    private fun addFilterButton(text: String, onClick: () -> Unit) {
        val button = Button(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        button.text = text
        button.isAllCaps = false
        button.textSize = 12f

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            dpToPx(36)
        )
        params.marginEnd = dpToPx(8)
        button.layoutParams = params

        button.setOnClickListener { onClick() }
        binding.filtersContainer.addView(button)
    }

    private fun applyFilters() {
        filteredEvents = when (currentFilter) {
            FILTER_ALL -> eventRepository.getPublicEvents()
            FILTER_THIS_WEEK -> eventRepository.getPublicEvents().filter { isEventThisWeek(it.date) }
            else -> eventRepository.getEventsByCategory(currentFilter)
        }
        applySearch()
    }

    private fun configureSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                applySearch()
            }
            override fun afterTextChanged(editable: Editable?) {}
        })
    }

    private fun applySearch() {
        val search = binding.etSearch.text.toString().trim().lowercase()
        if (search.isEmpty()) {
            showEvents(filteredEvents)
            return
        }
        val result = filteredEvents.filter { event ->
            event.title.lowercase().contains(search) ||
                    event.description.lowercase().contains(search) ||
                    event.category.lowercase().contains(search) ||
                    event.location.lowercase().contains(search)
        }
        showEvents(result)
    }

    private fun showEvents(events: List<Event>) {
        val session = SessionManager(requireContext())
        val currentUserId = if (session.isLoggedIn()) session.getUserId() else null

        binding.rvEvents.adapter = EventAdapter(events = events, currentUserId = currentUserId) { event ->
            navigateTo(R.id.eventDetailFragment, EventDetailFragment.args(event))
        }
    }

    private fun isEventThisWeek(eventDateText: String): Boolean {
        val eventDate = parseEventDate(eventDateText) ?: return false
        val startOfWeek = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endOfWeek = (startOfWeek.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 7)
        }

        val eventCalendar = Calendar.getInstance().apply {
            time = eventDate
        }

        return !eventCalendar.before(startOfWeek) && eventCalendar.before(endOfWeek)
    }

    private fun parseEventDate(value: String): Date? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
            SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        )
        for (format in formats) {
            format.isLenient = false
            try {
                return format.parse(value)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val FILTER_ALL = "ALL"
        private const val FILTER_THIS_WEEK = "THIS_WEEK"
    }
}