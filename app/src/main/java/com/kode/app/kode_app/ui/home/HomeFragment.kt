package com.kode.app.kode_app.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kode.app.kode_app.components.EventAdapter
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.core.DateFormats
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.repository.EventRepository
import com.kode.app.kode_app.databinding.FragmentHomeBinding
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.events.EventDetailFragment
import com.kode.app.kode_app.ui.events.EventFormFragment
import com.kode.app.kode_app.ui.navigateTo
import com.kode.app.kode_app.ui.showError
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null

    private val binding get() = _binding!!

    private val eventRepository = EventRepository()

    private var filteredEvents: List<Event> = emptyList()

    private var currentFilter: String = FILTER_ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvEvents.layoutManager = LinearLayoutManager(requireContext())
        loadFilters()
        applyFilters()
        configureSearch()
        configureCreateEventButton()
    }

    private fun configureCreateEventButton() {
        binding.btnCrearEvento.setOnClickListener {
            val fragment = if (SessionManager().isLoggedIn()) EventFormFragment() else LoginFragment.newInstance(destination = "CREATE_EVENT")
            navigateTo(fragment, addToBackStack = true)
        }
    }

    /** Botones fijos ("Todos", "Esta semana") y uno por cada categoría que exista en Supabase. */
    private fun loadFilters() {
        binding.filtersContainer.removeAllViews()
        addFilterButton("Todos") { selectFilter(FILTER_ALL) }
        addFilterButton("Esta semana") { selectFilter(FILTER_THIS_WEEK) }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = eventRepository.getCategories()) {
                is AppResult.Success -> result.value.forEach { category -> addFilterButton(category) { selectFilter(category) } }
                // Si fallan las categorías no se avisa aparte: la carga de eventos ya informa del problema
                is AppResult.Failure -> Unit
            }
        }
    }

    private fun selectFilter(filter: String) {
        currentFilter = filter
        applyFilters()
    }

    private fun addFilterButton(text: String, onClick: () -> Unit) {
        val button = Button(requireContext())
        button.text = text
        button.isAllCaps = false
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.marginEnd = dpToPx(8)
        button.layoutParams = params
        button.setOnClickListener { onClick() }
        binding.filtersContainer.addView(button)
    }

    /** Pide a Supabase los eventos del filtro actual y luego aplica el texto de búsqueda. */
    private fun applyFilters() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = when (currentFilter) {
                FILTER_ALL -> eventRepository.getEvents()
                FILTER_THIS_WEEK -> when (val all = eventRepository.getEvents()) {
                    is AppResult.Success -> AppResult.Success(all.value.filter { DateFormats.isThisWeek(it.date) })
                    is AppResult.Failure -> all
                }
                else -> eventRepository.getEventsByCategory(currentFilter)
            }
            when (result) {
                is AppResult.Success -> {
                    filteredEvents = result.value
                    applySearch()
                }
                is AppResult.Failure -> showError(result.error)
            }
        }
    }

    private fun configureSearch() {
        binding.etSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = applySearch()
                override fun afterTextChanged(editable: Editable?) = Unit
            }
        )
    }

    private fun applySearch() {
        val search = binding.etSearch.text.toString().trim().lowercase()
        if (search.isEmpty()) {
            showEvents(filteredEvents)
            return
        }
        showEvents(
            filteredEvents.filter { event ->
                event.title.lowercase().contains(search) || event.description.lowercase().contains(search) ||
                    event.category.lowercase().contains(search) || event.location.lowercase().contains(search)
            }
        )
    }

    private fun showEvents(events: List<Event>) {
        binding.rvEvents.adapter = EventAdapter(events = events, currentUserId = SessionManager().getUserId()) { event ->
            navigateTo(EventDetailFragment.newInstance(event), addToBackStack = true)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val FILTER_ALL = "ALL"
        private const val FILTER_THIS_WEEK = "THIS_WEEK"
    }
}
