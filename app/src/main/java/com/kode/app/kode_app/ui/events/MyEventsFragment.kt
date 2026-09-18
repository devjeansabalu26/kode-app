package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.kode.app.kode_app.R
import com.kode.app.kode_app.components.EventAdapter
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.ui.home.HomeFragment

class MyEventsFragment : Fragment() {

    private lateinit var session:
            SessionManager

    private lateinit var userRepository:
            UserRepository

    private lateinit var eventRepository:
            EventRepository

    private lateinit var recyclerView:
            RecyclerView

    private lateinit var layoutEmpty:
            LinearLayout

    private lateinit var tvEmpty:
            TextView

    private lateinit var btnEmptyAction:
            Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_my_events,
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

        recyclerView =
            view.findViewById(
                R.id.rvMyEvents
            )

        layoutEmpty =
            view.findViewById(
                R.id.layoutEmpty
            )

        tvEmpty =
            view.findViewById(
                R.id.tvEmpty
            )

        btnEmptyAction =
            view.findViewById(
                R.id.btnEmptyAction
            )

        val tabs =
            view.findViewById<TabLayout>(
                R.id.tabMyEvents
            )

        recyclerView.layoutManager =
            LinearLayoutManager(
                requireContext()
            )

        if (
            !session.isLoggedIn() ||
            userRepository.getUserById(
                session.getUserId()
            ) == null
        ) {

            session.logout()

            tabs.visibility =
                View.GONE

            showEmpty(
                message =
                    "Inicia sesión para ver tus eventos",

                actionText =
                    "Iniciar sesión"
            ) {

                parentFragmentManager
                    .beginTransaction()
                    .replace(
                        R.id.mainContainer,

                        LoginFragment.newInstance(
                            destination =
                                "MY_EVENTS"
                        )
                    )
                    .addToBackStack(null)
                    .commit()
            }

            return
        }

        tabs.addTab(
            tabs.newTab()
                .setText("Asistiré")
        )

        tabs.addTab(
            tabs.newTab()
                .setText("Organizados")
        )

        tabs.addOnTabSelectedListener(
            object :
                TabLayout.OnTabSelectedListener {

                override fun onTabSelected(
                    tab: TabLayout.Tab
                ) {

                    showTab(
                        tab.position
                    )
                }

                override fun onTabUnselected(
                    tab: TabLayout.Tab
                ) = Unit

                override fun onTabReselected(
                    tab: TabLayout.Tab
                ) = Unit
            }
        )

        showTab(0)
    }

    private fun showTab(
        position: Int
    ) {

        val userId =
            session.getUserId()

        if (position == 0) {

            showEvents(
                events =
                    eventRepository
                        .getEventsRegisteredByUser(
                            userId
                        ),

                emptyMessage =
                    "Aún no te has inscrito a ningún evento",

                actionText =
                    "Explorar eventos"
            ) {

                openFragment(
                    HomeFragment()
                )
            }

        } else {

            showEvents(
                events =
                    eventRepository
                        .getEventsCreatedByUser(
                            userId
                        ),

                emptyMessage =
                    "Aún no has creado ningún evento",

                actionText =
                    "+ Crear evento"
            ) {

                openFragment(
                    EventFormFragment()
                )
            }
        }
    }

    private fun showEvents(
        events: List<Event>,
        emptyMessage: String,
        actionText: String,
        onAction: () -> Unit
    ) {

        if (events.isEmpty()) {

            recyclerView.adapter =
                null

            showEmpty(
                emptyMessage,
                actionText,
                onAction
            )

            return
        }

        layoutEmpty.visibility =
            View.GONE

        recyclerView.visibility =
            View.VISIBLE

        recyclerView.adapter =
            EventAdapter(
                events = events,
                currentUserId =
                    session.getUserId()
            ) { event ->

                openFragment(
                    EventDetailFragment
                        .newInstance(
                            event
                        )
                )
            }
    }

    private fun showEmpty(
        message: String,
        actionText: String,
        onAction: () -> Unit
    ) {

        recyclerView.visibility =
            View.GONE

        layoutEmpty.visibility =
            View.VISIBLE

        tvEmpty.text =
            message

        btnEmptyAction.text =
            actionText

        btnEmptyAction.setOnClickListener {

            onAction()
        }
    }

    private fun openFragment(
        fragment: Fragment
    ) {

        parentFragmentManager
            .beginTransaction()
            .replace(
                R.id.mainContainer,
                fragment
            )
            .addToBackStack(null)
            .commit()
    }
}
