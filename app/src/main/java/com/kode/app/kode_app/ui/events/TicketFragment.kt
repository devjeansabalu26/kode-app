package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.core.SessionManager

class TicketFragment :
    Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.fragment_ticket,
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

        val session =
            SessionManager(
                requireContext()
            )

        val eventTitle =
            arguments
                ?.getString(
                    ARG_EVENT_TITLE
                )
                ?: ""

        val qrCode =
            arguments
                ?.getString(
                    ARG_QR_CODE
                )
                ?: ""

        view.findViewById<TextView>(
            R.id.tvTicketUser
        ).text =
            session.getName()

        view.findViewById<TextView>(
            R.id.tvTicketEvent
        ).text =
            eventTitle

        view.findViewById<TextView>(
            R.id.tvTicketCode
        ).text =
            qrCode
    }

    companion object {

        private const val ARG_EVENT_ID =
            "event_id"

        private const val ARG_EVENT_TITLE =
            "event_title"

        private const val ARG_QR_CODE =
            "qr_code"

        fun newInstance(
            eventId: Int,
            eventTitle: String,
            qrCode: String
        ): TicketFragment {

            return TicketFragment()
                .apply {

                    arguments =
                        Bundle().apply {

                            putInt(
                                ARG_EVENT_ID,
                                eventId
                            )

                            putString(
                                ARG_EVENT_TITLE,
                                eventTitle
                            )

                            putString(
                                ARG_QR_CODE,
                                qrCode
                            )
                        }
                }
        }
    }
}