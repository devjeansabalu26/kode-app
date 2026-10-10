package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.databinding.FragmentTicketBinding

class TicketFragment : Fragment() {

    private var _binding: FragmentTicketBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTicketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val eventId = arguments?.getInt(ARG_EVENT_ID, -1) ?: -1
        val eventTitle = arguments?.getString(ARG_EVENT_TITLE).orEmpty()
        val qrCodeParam = arguments?.getString(ARG_QR_CODE).orEmpty()

        val eventRepository = EventRepository(requireContext())
        val userRepository = UserRepository(requireContext())
        val sessionManager = SessionManager(requireContext())

        val event = eventRepository.getEventById(eventId)
        val user = sessionManager.getUserId().let { userRepository.getUserById(it) }

        if (event != null && user != null) {
            binding.tvTicketCategory.text = event.category.uppercase()
            binding.tvTicketEvent.text = if (eventTitle.isNotEmpty()) eventTitle else event.title
            binding.tvTicketUser.text = user.name

            val finalCode = if (qrCodeParam.isNotEmpty()) qrCodeParam else "EVT-${event.id}-${user.id}"
            binding.tvTicketCode.text = "Acceso General • ID: #$finalCode"

            binding.tvTicketDate.text = event.date
            binding.tvTicketTime.text = if (!event.endTime.isNullOrEmpty()) event.endTime else "Hora fijada"
        }

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnShareTicket.setOnClickListener {
            Toast.makeText(requireContext(), "Preparando boleto para compartir...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_EVENT_TITLE = "event_title"
        private const val ARG_QR_CODE = "qr_code"

        fun newInstance(eventId: Int, eventTitle: String? = null, qrCode: String? = null): TicketFragment {
            return TicketFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_EVENT_ID, eventId)
                    putString(ARG_EVENT_TITLE, eventTitle)
                    putString(ARG_QR_CODE, qrCode)
                }
            }
        }
    }
}