package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.kode.app.kode_app.R
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.core.navigateTo
import com.kode.app.kode_app.core.goBack
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.data.RegistrationRepository
import com.kode.app.kode_app.databinding.FragmentEventDetailBinding
import com.kode.app.kode_app.model.AttendeeItem
import com.kode.app.kode_app.model.Event
import com.kode.app.kode_app.retrofit.ClienteClimaRetrofit
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.retrofit.response.ClimaResponse
import com.kode.app.kode_app.util.Constantes
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class EventDetailFragment : Fragment() {

    private var _binding: FragmentEventDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var eventRepository: EventRepository
    private lateinit var registrationRepository: RegistrationRepository
    private lateinit var session: SessionManager
    private lateinit var attendeeAdapter: AttendeeAdapter

    private var fullAttendeeList: List<AttendeeItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { goBack() }
        binding.btnBackPortal.setOnClickListener { goBack() }

        eventRepository = EventRepository()
        registrationRepository = RegistrationRepository()
        session = SessionManager(requireContext())

        val eventId = arguments?.getInt(ARG_ID, -1) ?: -1
        if (eventId <= 0) {
            showMessage("No se pudo identificar el evento")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val event = eventRepository.getEventById(eventId)
            if (event == null) {
                showMessage("El evento no existe")
                return@launch
            }

            val currentUserId = if (session.isLoggedIn()) session.getUserId() else -1L
            val isCreator = currentUserId > 0 && event.creatorId != null && event.creatorId == currentUserId

            if (isCreator) {
                binding.containerPublicDetail.isVisible = false
                binding.bottomActionContainer.isVisible = false
                binding.containerOrganizerPortal.isVisible = true

                setupCreatorDashboard(event)
            } else {
                binding.containerPublicDetail.isVisible = true
                binding.bottomActionContainer.isVisible = true
                binding.containerOrganizerPortal.isVisible = false

                setupParticipantView(event)
            }
        }
    }

    private suspend fun setupParticipantView(event: Event) {
        binding.tvCategoryLabel.text = event.category.uppercase()
        binding.tvDetailTitle.text = event.title
        binding.tvDetailDescription.text = event.description
        binding.tvDetailDate.text = " ${event.date}"
        binding.tvDetailLocation.text = "${event.location}"

        val endTimeText = if (!event.endTime.isNullOrEmpty()) " - ${event.endTime}" else ""
        binding.tvDetailTime.text = "Horario: ${event.date}$endTimeText"
        binding.tvEventTypeBadge.text = " Modalidad: ${event.eventType ?: "Presencial"}"

        if (!event.accessCode.isNullOrEmpty()) {
            binding.tvCodeBadge.text = "CÓDIGO: ${event.accessCode}"
            binding.tvCodeBadge.isVisible = true
        } else {
            binding.tvCodeBadge.isVisible = false
        }

        val registered = event.registeredCount
        val capacity = event.capacity
        val remaining = (capacity - registered).coerceAtLeast(0)
        val capacityPercent = calculatePercentage(registered, capacity)

        binding.tvRegisteredCount.text = " $registered / $capacity Registrados"
        binding.tvRemainingSeats.text = "$remaining cupos restantes"
        binding.tvCapacityPercent.text = "$capacityPercent% ocupado"
        binding.pbCapacity.progress = capacityPercent

        binding.tvReqDni.isVisible = event.requireDni
        binding.tvReqPass.text = if (event.isUntransferable) {
            "Pase Personal e Intransferible con QR"
        } else {
            "Pase digital con código QR alfanumérico"
        }

        loadBanner(event)
        setupMap(event)
        loadWeather(event)

        val userId = if (session.isLoggedIn()) session.getUserId() else -1L
        val alreadyRegistered = userId > 0 && registrationRepository.isUserRegisteredInEvent(event.id, userId)
        when {
            alreadyRegistered -> {
                binding.btnRegister.text = "Ver mi pase"
                binding.btnRegister.isEnabled = true
            }
            remaining == 0 -> {
                binding.btnRegister.text = "Aforo completo"
                binding.btnRegister.isEnabled = false
            }
            else -> {
                binding.btnRegister.text = "Quiero asistir"
                binding.btnRegister.isEnabled = true
            }
        }

        binding.btnRegister.setOnClickListener {
            if (!session.isLoggedIn()) {
                showMessage("Inicia sesión para inscribirte")
                navigateTo(
                    R.id.loginFragment,
                    LoginFragment.args(destination = "REGISTER_EVENT", eventId = event.id, eventTitle = event.title)
                )
                return@setOnClickListener
            }

            binding.btnRegister.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val qrCode = registrationRepository.registerUserInEvent(event.id, session.getUserId())
                binding.btnRegister.isEnabled = true
                if (qrCode == null) {
                    showMessage("No se pudo completar la inscripción")
                    return@launch
                }

                if (!alreadyRegistered) showMessage("¡Inscripción confirmada!")
                navigateTo(R.id.ticketFragment, TicketFragment.args(eventId = event.id, eventTitle = event.title, qrCode = qrCode))
            }
        }
    }

    private fun loadBanner(event: Event) {
        Glide.with(this)
            .load(event.bannerUrl())
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .centerCrop()
            .into(binding.ivDetailBanner)
    }

    private fun setupMap(event: Event) {
        val lat = event.latitude ?: return
        val lng = event.longitude ?: return
        val position = LatLng(lat, lng)

        binding.cardDetailMap.isVisible = true
        val mapFragment = SupportMapFragment.newInstance(
            GoogleMapOptions().liteMode(true).mapToolbarEnabled(true)
        )
        childFragmentManager.beginTransaction()
            .replace(R.id.mapDetailContainer, mapFragment)
            .commitAllowingStateLoss()

        mapFragment.getMapAsync { map ->
            map.addMarker(MarkerOptions().position(position).title(event.location))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
        }
    }

    private fun loadWeather(event: Event) {
        val lat = event.latitude ?: return
        val lng = event.longitude ?: return
        val eventDay = event.date.take(10)
        val daysUntilEvent = daysUntil(eventDay) ?: return
        if (daysUntilEvent < 0) return

        binding.cardWeather.isVisible = true
        if (daysUntilEvent > Constantes.DIAS_MAX_PRONOSTICO) {
            binding.tvWeather.text = "El pronóstico estará disponible 16 días antes del evento."
            return
        }

        val request: Call<ClimaResponse> = ClienteClimaRetrofit
            .retrofitClimaService.obtenerPronostico(lat, lng, eventDay, eventDay)
        request.enqueue(object : Callback<ClimaResponse> {
            override fun onResponse(call: Call<ClimaResponse>, response: Response<ClimaResponse>) {
                val binding = _binding ?: return
                val daily = response.body()?.daily
                if (!response.isSuccessful || daily == null || daily.time.isEmpty()) {
                    binding.tvWeather.text = "No hay pronóstico disponible para esta fecha."
                    return
                }
                val estado = ClienteClimaRetrofit.describirClima(daily.weatherCode.firstOrNull())
                val max = daily.temperaturaMax.firstOrNull()
                val min = daily.temperaturaMin.firstOrNull()
                val lluvia = daily.probabilidadLluvia.firstOrNull()
                binding.tvWeather.text = buildString {
                    append(estado)
                    if (min != null && max != null) append("  ·  ${min.toInt()}° / ${max.toInt()}°C")
                    if (lluvia != null) append("\nProbabilidad de lluvia: $lluvia%")
                }
            }

            override fun onFailure(call: Call<ClimaResponse>, t: Throwable) {
                val binding = _binding ?: return
                binding.tvWeather.text = "No se pudo obtener el clima (revisa tu conexión)."
            }
        })
    }

    private fun daysUntil(day: String): Long? {
        val eventDate = try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(day)
        } catch (_: Exception) {
            null
        } ?: return null
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return TimeUnit.MILLISECONDS.toDays(eventDate.time - today.timeInMillis)
    }

    private fun setupCreatorDashboard(event: Event) {
        binding.tvEventSubtitle.text = " ${event.title} · ${event.date}"

        updateDashboardMetrics(event)

        attendeeAdapter = AttendeeAdapter(emptyList()) { attendee, newStatus ->
            viewLifecycleOwner.lifecycleScope.launch {
                registrationRepository.updateAttendanceStatus(event.id, attendee.user.id, newStatus)

                eventRepository.getEventById(event.id)?.let { updatedEvent ->
                    updateDashboardMetrics(updatedEvent)
                }
            }
        }

        binding.rvAttendees.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = attendeeAdapter
        }

        loadAttendees(event.id)

        binding.etSearchAttendee.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAttendees(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnQuickScanBanner.setOnClickListener { openQrScanner(event.id) }
        binding.btnEditEvent.setOnClickListener {
            navigateTo(R.id.eventEditFragment, EventFormFragment.editArgs(event.id))
        }
        binding.btnOpenScanner.setOnClickListener { openQrScanner(event.id) }
    }

    private fun loadAttendees(eventId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            fullAttendeeList = registrationRepository.getAttendeesForEvent(eventId)
            filterAttendees(binding.etSearchAttendee.text.toString())
        }
    }

    private fun filterAttendees(query: String) {
        val filtered = if (query.isBlank()) {
            fullAttendeeList
        } else {
            fullAttendeeList.filter {
                it.user.name.contains(query, ignoreCase = true) ||
                        it.ticketCode.contains(query, ignoreCase = true)
            }
        }
        attendeeAdapter.updateData(filtered)
    }

    private fun updateDashboardMetrics(event: Event) {
        binding.tvStatTotal.text = event.capacity.toString()
        binding.tvStatAttended.text = event.attendedCount.toString()

        val pendingCount = (event.registeredCount - event.attendedCount).coerceAtLeast(0)
        binding.tvStatPending.text = pendingCount.toString()

        val absentCount = (event.capacity - event.registeredCount).coerceAtLeast(0)
        binding.tvStatAbsent.text = absentCount.toString()

        binding.tvStatAttendedPercent.text = "${calculatePercentage(event.attendedCount, event.capacity)}%"
        binding.tvStatPendingPercent.text = "${calculatePercentage(pendingCount, event.capacity)}%"
        binding.tvStatAbsentPercent.text = "${calculatePercentage(absentCount, event.capacity)}%"
    }

    private fun openQrScanner(eventId: Int) {
        val args = Bundle().apply {
            putInt("EVENT_ID", eventId)
        }
        navigateTo(R.id.qrScannerFragment, args)
    }

    private fun calculatePercentage(value: Int, maximum: Int): Int {
        if (maximum <= 0) return 0
        return (value * 100 / maximum).coerceIn(0, 100)
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_ID = "id"
        private const val ARG_TITLE = "title"

        fun args(event: Event): Bundle {
            return Bundle().apply {
                putInt(ARG_ID, event.id)
                putString(ARG_TITLE, event.title)
            }
        }
    }
}