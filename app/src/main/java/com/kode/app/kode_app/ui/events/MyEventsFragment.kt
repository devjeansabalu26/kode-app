package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.kode.app.kode_app.R
import com.kode.app.kode_app.databinding.FragmentMyEventsBinding
import com.kode.app.kode_app.components.EventAdapter
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.core.goToTab
import com.kode.app.kode_app.core.navigateTo
import com.kode.app.kode_app.core.goBack
import com.kode.app.kode_app.ui.auth.LoginFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MyEventsFragment : Fragment() {
    private var _binding: FragmentMyEventsBinding? = null

    private val binding get() = _binding!!

    private lateinit var session: SessionManager

    private lateinit var userRepository: UserRepository

    private lateinit var eventRepository: EventRepository

    private lateinit var recyclerView: RecyclerView

    private lateinit var layoutEmpty: LinearLayout

    private lateinit var tvEmpty: TextView

    private lateinit var btnEmptyAction: Button

    private var loadJob: Job? = null

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
        binding.btnBack.setOnClickListener { goBack() }
        session = SessionManager(requireContext())
        userRepository = UserRepository()
        eventRepository = EventRepository()
        recyclerView = binding.rvMyEvents
        layoutEmpty = binding.layoutEmpty
        tvEmpty = binding.tvEmpty
        btnEmptyAction = binding.btnEmptyAction
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        if (!session.isLoggedIn()) {
            showLoggedOut()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            if (userRepository.getUserById(session.getUserId()) == null) {
                showLoggedOut()
                return@launch
            }
            setupTabs()
        }
    }

    private fun showLoggedOut() {
        session.logout()
        binding.tabMyEvents.visibility = View.GONE
        binding.swipeRefreshMyEvents.isEnabled = false
        showEmpty(message = "Inicia sesión para ver tus eventos", actionText = "Iniciar sesión") {
            navigateTo(R.id.loginFragment, LoginFragment.args(destination = "MY_EVENTS"))
        }
    }

    private fun setupTabs() {
        val tabs = binding.tabMyEvents
        tabs.addTab(tabs.newTab().setText("Asistiré"))
        tabs.addTab(tabs.newTab().setText("Organizados"))
        tabs.addOnTabSelectedListener(
            object :
                TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    showTab(tab.position)
                }
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            }
        )
        showTab(0)

        binding.swipeRefreshMyEvents.setOnChildScrollUpCallback { _, _ ->
            recyclerView.isVisible && recyclerView.canScrollVertically(-1)
        }
        binding.swipeRefreshMyEvents.setOnRefreshListener {
            showTab(tabs.selectedTabPosition.coerceAtLeast(0))
        }
    }

    private fun showTab(position: Int) {
        val userId = session.getUserId()
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            if (position == 0) {
                showEvents(
                    events = eventRepository.getEventsRegisteredByUser(userId),
                    emptyMessage = "Aún no te has inscrito a ningún evento",
                    actionText = "Explorar eventos"
                ) {
                    goToTab(R.id.nav_home)
                }
            } else {
                showEvents(
                    events = eventRepository.getEventsCreatedByUser(userId),
                    emptyMessage = "Aún no has creado ningún evento",
                    actionText = "+ Crear evento"
                ) {
                    goToTab(R.id.nav_create_event)
                }
            }
            binding.swipeRefreshMyEvents.isRefreshing = false
        }
    }

    private fun showEvents(events: List<Event>, emptyMessage: String, actionText: String, onAction: () -> Unit) {
        if (events.isEmpty()) {
            recyclerView.adapter = null
            showEmpty(emptyMessage, actionText, onAction)
            return
        }
        layoutEmpty.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        recyclerView.adapter = EventAdapter(events = events, currentUserId = session.getUserId()) { event ->
                navigateTo(R.id.eventDetailFragment, EventDetailFragment.args(event))
            }
    }

    private fun showEmpty(message: String, actionText: String, onAction: () -> Unit) {
        recyclerView.visibility = View.GONE
        layoutEmpty.visibility = View.VISIBLE
        tvEmpty.text = message
        btnEmptyAction.text = actionText
        btnEmptyAction.setOnClickListener {
            onAction()
        }
    }
}
