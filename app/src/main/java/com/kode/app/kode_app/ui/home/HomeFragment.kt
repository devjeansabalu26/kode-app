package com.kode.app.kode_app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kode.app.kode_app.R
import com.kode.app.kode_app.components.EventAdapter
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.ui.events.EventDetailFragment
class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_home,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        val recyclerView =
            view.findViewById<RecyclerView>(R.id.rvEvents)

        val events = listOf(

            Event(
                id = 1,
                title = "Conferencia de Tecnología & IA 2026",
                description = "Innovación, inteligencia artificial y desarrollo de software.",
                date = "20 Sep 2026 · 18:00",
                location = "Centro de Convenciones, Lima",
                category = "Tecnología"
            ),

            Event(
                id = 2,
                title = "Festival de Diseño y Arquitectura",
                description = "Diseño, creatividad y nuevas tendencias.",
                date = "26 Sep 2026 · 16:00",
                location = "Miraflores, Lima",
                category = "Diseño"
            ),

            Event(
                id = 3,
                title = "Summit Fundadores & Startups",
                description = "Comunidad, emprendimiento e innovación.",
                date = "05 Oct 2026 · 19:30",
                location = "San Isidro, Lima",
                category = "Comunidad"
            )
        )

        recyclerView.layoutManager =
            LinearLayoutManager(requireContext())

        recyclerView.adapter =
            EventAdapter(events) { event ->

                parentFragmentManager
                    .beginTransaction()
                    .replace(
                        R.id.mainContainer,
                        EventDetailFragment.newInstance(event)
                    )
                    .addToBackStack(null)
                    .commit()
            }
    }
}