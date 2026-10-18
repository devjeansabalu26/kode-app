package com.kode.app.kode_app.ui.events

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kode.app.kode_app.core.goBack
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.databinding.FragmentVirtualTicketBinding
import kotlinx.coroutines.launch

class VirtualTicketFragment : Fragment() {

    private var _binding: FragmentVirtualTicketBinding? = null
    private val binding get() = _binding!!

    private lateinit var eventRepository: EventRepository
    private var eventId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVirtualTicketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        eventRepository = EventRepository()

        eventId = arguments?.getInt(ARG_EVENT_ID, -1) ?: -1
        binding.btnBack.setOnClickListener { goBack() }

        if (eventId <= 0) {
            Toast.makeText(requireContext(), "No se pudo cargar el pase virtual", Toast.LENGTH_SHORT).show()
            goBack()
            return
        }

        loadVirtualEventDetails()
    }

    private fun loadVirtualEventDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            val event = eventRepository.getEventById(eventId)
            if (event == null) {
                Toast.makeText(requireContext(), "El evento ya no está disponible", Toast.LENGTH_SHORT).show()
                goBack()
                return@launch
            }

            binding.tvVirtualTitle.text = event.title
            binding.tvVirtualDetails.text = event.location // Aquí se guardó el formato: "Online: Zoom | URL: https... | Pass: ..."

            // Extraemos la URL de la transmisión del campo location para que el botón funcione de inmediato
            val locationText = event.location
            val urlExtraction = locationText.substringAfter("URL: ", "").substringBefore(" |", "")

            if (urlExtraction.isNotBlank()) {
                binding.btnOpenStream.setOnClickListener {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlExtraction.trim()))
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.btnOpenStream.isEnabled = false
                binding.btnOpenStream.text = "Enlace no disponible"
            }
        }
    }

    companion object {
        private const val ARG_EVENT_ID = "EVENT_ID"

        fun args(eventId: Int): Bundle = Bundle().apply {
            putInt(ARG_EVENT_ID, eventId)
        }
    }
}