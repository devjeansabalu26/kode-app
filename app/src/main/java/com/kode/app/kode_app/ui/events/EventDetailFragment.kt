package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.model.Event

class EventDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_event_detail,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        val tvCategory =
            view.findViewById<TextView>(R.id.tvDetailCategory)

        val tvTitle =
            view.findViewById<TextView>(R.id.tvDetailTitle)

        val tvDescription =
            view.findViewById<TextView>(R.id.tvDetailDescription)

        val tvDate =
            view.findViewById<TextView>(R.id.tvDetailDate)

        val tvLocation =
            view.findViewById<TextView>(R.id.tvDetailLocation)

        val btnRegister =
            view.findViewById<Button>(R.id.btnRegisterEvent)

        tvCategory.text =
            arguments?.getString(ARG_CATEGORY)

        tvTitle.text =
            arguments?.getString(ARG_TITLE)

        tvDescription.text =
            arguments?.getString(ARG_DESCRIPTION)

        tvDate.text =
            arguments?.getString(ARG_DATE)

        tvLocation.text =
            arguments?.getString(ARG_LOCATION)

        btnRegister.setOnClickListener {

            Toast.makeText(
                requireContext(),
                "Aquí validaremos si el usuario inició sesión",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {

        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_DATE = "date"
        private const val ARG_LOCATION = "location"
        private const val ARG_CATEGORY = "category"

        fun newInstance(event: Event): EventDetailFragment {

            return EventDetailFragment().apply {

                arguments = Bundle().apply {

                    putString(ARG_TITLE, event.title)
                    putString(
                        ARG_DESCRIPTION,
                        event.description
                    )
                    putString(ARG_DATE, event.date)
                    putString(
                        ARG_LOCATION,
                        event.location
                    )
                    putString(
                        ARG_CATEGORY,
                        event.category
                    )
                }
            }
        }
    }
}