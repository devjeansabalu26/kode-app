package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.kode.app.kode_app.components.EventAdapter
import com.kode.app.kode_app.core.AppResult
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.repository.EventRepository
import com.kode.app.kode_app.databinding.FragmentMyEventsBinding
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.home.HomeFragment
import com.kode.app.kode_app.ui.navigateTo
import com.kode.app.kode_app.ui.showError
import kotlinx.coroutines.launch

class MyEventsFragment : Fragment() {
    private var _binding: FragmentMyEventsBinding? = null

    private val binding get() = _binding!!

    private val session = SessionManager()

    private val eventRepository = EventRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvMyEvents.layoutManager = LinearLayoutManager(requireContext())
        val tabs = binding.tabMyEvents
        if (!session.isLoggedIn()) {
            tabs.visibility = View.GONE
            showEmpty("Inicia sesión para ver tus eventos", "Iniciar sesión") {
                navigateTo(LoginFragment.newInstance(destination = "MY_EVENTS"), addToBackStack = true)
            }
            return
        }
        tabs.addTab(tabs.newTab().setText("Asistiré"))
        tabs.addTab(tabs.newTab().setText("Organizados"))
        tabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            }
        )
        showTab(0)
    }

    private fun showTab(position: Int) {
        val userId = session.getUserId() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (position == 0) {
                showResult(eventRepository.getEventsRegisteredByUser(userId), "Aún no te has inscrito a ningún evento", "Explorar eventos") {
                    navigateTo(HomeFragment(), addToBackStack = true)
                }
            } else {
                showResult(eventRepository.getEventsCreatedByUser(userId), "Aún no has creado ningún evento", "+ Crear evento") {
                    navigateTo(EventFormFragment(), addToBackStack = true)
                }
            }
        }
    }

    private fun showResult(result: AppResult<List<Event>>, emptyMessage: String, actionText: String, onAction: () -> Unit) {
        when (result) {
            is AppResult.Failure -> showError(result.error)
            is AppResult.Success -> showEvents(result.value, emptyMessage, actionText, onAction)
        }
    }

    private fun showEvents(events: List<Event>, emptyMessage: String, actionText: String, onAction: () -> Unit) {
        if (events.isEmpty()) {
            binding.rvMyEvents.adapter = null
            showEmpty(emptyMessage, actionText, onAction)
            return
        }
        binding.layoutEmpty.visibility = View.GONE
        binding.rvMyEvents.visibility = View.VISIBLE
        binding.rvMyEvents.adapter = EventAdapter(events = events, currentUserId = session.getUserId()) { event ->
            navigateTo(EventDetailFragment.newInstance(event), addToBackStack = true)
        }
    }

    private fun showEmpty(message: String, actionText: String, onAction: () -> Unit) {
        binding.rvMyEvents.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = message
        binding.btnEmptyAction.text = actionText
        binding.btnEmptyAction.setOnClickListener { onAction() }
    }
}
