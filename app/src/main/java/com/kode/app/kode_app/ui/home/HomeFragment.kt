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
import androidx.recyclerview.widget.RecyclerView
import com.kode.app.kode_app.R
import com.kode.app.kode_app.databinding.FragmentHomeBinding
import com.kode.app.kode_app.components.EventAdapter
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.events.EventDetailFragment
import com.kode.app.kode_app.ui.events.EventFormFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.kode.app.kode_app.core.SessionManager
class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null

    private val binding get() = _binding!!

    private lateinit var eventRepository: EventRepository

    private lateinit var recyclerView: RecyclerView

    private lateinit var filtersContainer: LinearLayout

    private lateinit var etSearch: EditText

    private var allEvents: List<Event> = emptyList()

    private var filteredEvents: List<Event> = emptyList()

    private var currentFilter: String = FILTER_ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eventRepository = EventRepository(requireContext())
        recyclerView = binding.rvEvents
        filtersContainer = binding.filtersContainer
        etSearch = binding.etSearch
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadEvents()
        loadFilters()
        configureSearch()
        configureCreateEventButton()
    }

    private fun configureCreateEventButton() {
        binding.btnCrearEvento.setOnClickListener {
            val session = SessionManager(requireContext())
            val fragment = if (session.isLoggedIn()) {
                    EventFormFragment()
                } else {
                    LoginFragment.newInstance(destination = "CREATE_EVENT")
                }
            parentFragmentManager.beginTransaction().replace(R.id.mainContainer, fragment).addToBackStack(null).commit()
        }
    }

    private fun loadEvents() {
        allEvents = eventRepository.getEvents()
        filteredEvents = allEvents
        showEvents(filteredEvents)
    }

    private fun loadFilters() {
        filtersContainer.removeAllViews()
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
        val button = Button(requireContext())
        button.text = text
        button.isAllCaps = false
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.marginEnd = dpToPx(8)
        button.layoutParams = params
        button.setOnClickListener {
            onClick()
        }
        filtersContainer.addView(button)
    }

    private fun applyFilters() {
        filteredEvents = when (currentFilter) {
                FILTER_ALL -> {
                    eventRepository.getEvents()
                }
                FILTER_THIS_WEEK -> {
                    eventRepository.getEvents().filter { event ->
                            isEventThisWeek(event.date)
                        }
                }
                else -> {
                    eventRepository.getEventsByCategory(currentFilter)
                }
            }
        applySearch()
    }

    private fun configureSearch() {
        etSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
                }
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                    applySearch()
                }
                override fun afterTextChanged(editable: Editable?) {
                }
            }
        )
    }

    private fun applySearch() {
        val search = etSearch.text.toString().trim().lowercase()
        if (search.isEmpty()) {
            showEvents(filteredEvents)
            return
        }
        val result = filteredEvents.filter { event ->
                event.title.lowercase().contains(search)
                        ||
                        event.description.lowercase().contains(search)
                        ||
                        event.category.lowercase().contains(search)
                        ||
                        event.location.lowercase().contains(search)
            }
        showEvents(result)
    }

    private fun showEvents(events: List<Event>) {
        val session = SessionManager(requireContext())
        val currentUserId = if (session.isLoggedIn()) {
                session.getUserId()
            } else {
                null
            }
        recyclerView.adapter = EventAdapter(events = events, currentUserId = currentUserId) { event ->
                parentFragmentManager.beginTransaction().replace(R.id.mainContainer, EventDetailFragment.newInstance(event)).addToBackStack(null)
                    .commit()
            }
    }

    private fun isEventThisWeek(eventDateText: String): Boolean {
        val eventDate = parseEventDate(eventDateText) ?: return false
        val now = Calendar.getInstance()
        val startOfWeek = Calendar.getInstance()
        startOfWeek.firstDayOfWeek = Calendar.MONDAY
        startOfWeek.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        startOfWeek.set(Calendar.HOUR_OF_DAY, 0)
        startOfWeek.set(Calendar.MINUTE, 0)
        startOfWeek.set(Calendar.SECOND, 0)
        startOfWeek.set(Calendar.MILLISECOND, 0)
        val endOfWeek = startOfWeek.clone()
                    as Calendar
        endOfWeek.add(Calendar.DAY_OF_YEAR, 7)
        val eventCalendar = Calendar.getInstance()
        eventCalendar.time = eventDate
        return !eventCalendar.before(startOfWeek)
                &&
                eventCalendar.before(endOfWeek)
    }

    private fun parseEventDate(value: String): Date? {
        val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
                SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.ENGLISH),
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            )
        formats.forEach {
            it.isLenient = false
            try {
                val date = it.parse(value)
                if (date != null) {
                    return date
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val FILTER_ALL = "ALL"
        private const val FILTER_THIS_WEEK = "THIS_WEEK"
    }
}