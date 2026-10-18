package com.kode.app.kode_app.ui.events

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.kode.app.kode_app.R
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.core.goBack
import com.kode.app.kode_app.core.navigateTo
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.data.ImageRepository
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.databinding.FragmentEventFormBinding
import com.kode.app.kode_app.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EventFormFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentEventFormBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionManager
    private lateinit var userRepository: UserRepository
    private lateinit var eventRepository: EventRepository

    private val selectedDateTime = Calendar.getInstance()
    private var dateSelected = false
    private var timeSelected = false
    private var selectedEndTime = ""

    private var map: GoogleMap? = null
    private var marker: Marker? = null
    private var selectedLatLng: LatLng? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var selectedImageUri: Uri? = null
    private var editingEvent: Event? = null
    private val editEventId: Int get() = arguments?.getInt(ARG_EVENT_ID, -1) ?: -1
    private val isEditMode: Boolean get() = editEventId > 0

    companion object {
        private const val ARG_EVENT_ID = "event_id"
        fun editArgs(eventId: Int): Bundle = Bundle().apply {
            putInt(ARG_EVENT_ID, eventId)
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) showSelectedImage(uri)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableMyLocation()
        } else {
            showMessage("Permiso de ubicación denegado")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        map = null
        marker = null
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        userRepository = UserRepository()
        eventRepository = EventRepository()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if (!session.isLoggedIn()) {
            openLogin()
            return
        }
        val userId = session.getUserId()
        if (userId <= 0) {
            session.logout()
            openLogin()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            if (userRepository.getUserById(userId) == null) {
                session.logout()
                openLogin()
            }
        }

        binding.etEventDate.setOnClickListener { showDatePicker(binding.etEventDate) }
        binding.etStartTime.setOnClickListener { showTimePicker(binding.etStartTime, isStart = true) }
        binding.etEndTime.setOnClickListener { showTimePicker(binding.etEndTime, isStart = false) }

        binding.btnBack.setOnClickListener { closeForm() }
        binding.btnCancel.setOnClickListener { closeForm() }
        binding.btnSaveDraft.setOnClickListener { showMessage("Borrador guardado") }
        binding.btnUploadImage.setOnClickListener {
            pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        setupEventTypeToggle()
        setupVirtualPlatformSpinner()
        setupPrivacyToggle()
        setupMap()

        if (isEditMode) loadEventForEdit(userId)

        binding.btnPublish.setOnClickListener {
            val title = binding.etEventTitle.text.toString().trim()
            val description = binding.etEventDescription.text.toString().trim()
            val capacityText = binding.etCapacity.text.toString().trim()

            val selectedChipId = binding.chipGroupCategory.checkedChipId
            if (selectedChipId == View.NO_ID) { showMessage("Selecciona una categoría"); return@setOnClickListener }
            val category = binding.root.findViewById<Chip>(selectedChipId).text.toString().replace("⚡", "").trim()

            val checkedEventTypeId = binding.toggleEventType.checkedButtonId
            val isPresencial = checkedEventTypeId == R.id.btnPresencial || checkedEventTypeId == View.NO_ID
            val isVirtual = checkedEventTypeId == R.id.btnVirtual
            val isHibrido = checkedEventTypeId == R.id.btnHibrido

            val eventType = when {
                isVirtual -> "Virtual"
                isHibrido -> "Híbrido"
                else -> "Presencial"
            }

            var location = ""
            if (isPresencial || isHibrido) {
                location = binding.etLocation.text.toString().trim()
                if (location.isEmpty()) { showMessage("Ingresa o marca la dirección en el mapa"); return@setOnClickListener }
            }


            if (isVirtual || isHibrido) {
                val streamUrl = binding.etStreamUrl.text.toString().trim()
                val streamPassword = binding.etMeetingPassword.text.toString().trim()
                val streamInstructions = binding.etAccessInstructions.text.toString().trim()
                if (streamUrl.isEmpty()) { showMessage("Ingresa el enlace de la transmisión"); return@setOnClickListener }

                val platformName = binding.spPlatform.selectedItem.toString()
                location = buildString {
                    append("Online: $platformName | URL: $streamUrl")
                    if (streamPassword.isNotEmpty()) append(" | Pass: $streamPassword")
                    if (streamInstructions.isNotEmpty()) append(" | Nota: $streamInstructions")
                }
            }

            val isPrivate = binding.togglePrivacy.checkedButtonId == R.id.btnPrivado
            // Si es privado, el repositorio genera o mantiene el código automáticamente si está vacío

            if (title.isEmpty()) { showMessage("Ingresa el nombre del evento"); return@setOnClickListener }
            if (description.isEmpty()) { showMessage("Ingresa una descripción"); return@setOnClickListener }
            if (!dateSelected) { showMessage("Selecciona la fecha"); return@setOnClickListener }
            if (!timeSelected) { showMessage("Selecciona la hora de inicio"); return@setOnClickListener }

            val capacity = capacityText.toIntOrNull()
            if (capacity == null || capacity <= 0) {
                showMessage("Ingresa una capacidad válida")
                return@setOnClickListener
            }

            val registeredCount = editingEvent?.registeredCount ?: 0
            if (capacity < registeredCount) {
                showMessage("La capacidad no puede ser menor a los $registeredCount inscritos")
                return@setOnClickListener
            }

            if (selectedDateTime.timeInMillis <= System.currentTimeMillis()) {
                showMessage("La fecha del evento debe ser futura")
                return@setOnClickListener
            }

            val creatorId = session.getUserId() // Es Long según UserRepository
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val databaseDate = formatter.format(selectedDateTime.time)

            val requireDni = binding.cbRequireDni.isChecked

            binding.btnPublish.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val imageUri = selectedImageUri
                val imageUrl = if (imageUri != null) {
                    showMessage("Subiendo imagen...")
                    ImageRepository(requireContext()).uploadEventImage(imageUri) ?: run {
                        binding.btnPublish.isEnabled = true
                        showMessage("No se pudo subir la imagen")
                        return@launch
                    }
                } else {
                    editingEvent?.imageUrl.orEmpty()
                }

                val event = editingEvent
                if (event != null) {
                    val saved = eventRepository.updateEvent(
                        eventId = event.id,
                        creatorId = creatorId,
                        title = title,
                        description = description,
                        date = databaseDate,
                        location = location,
                        category = category,
                        capacity = capacity,
                        endTime = selectedEndTime,
                        eventType = eventType,
                        isPrivate = isPrivate,
                        requireDni = requireDni,
                        isUntransferable = false,
                        latitude = if (isPresencial || isHibrido) selectedLatLng?.latitude else null,
                        longitude = if (isPresencial || isHibrido) selectedLatLng?.longitude else null,
                        imageUrl = imageUrl
                    )
                    binding.btnPublish.isEnabled = true
                    if (!saved) {
                        showMessage("No se pudo actualizar el evento")
                        return@launch
                    }
                    showMessage("Evento actualizado correctamente")
                    closeForm()
                } else {
                    val eventId = eventRepository.createEvent(
                        creatorId = creatorId,
                        title = title,
                        description = description,
                        date = databaseDate,
                        location = location,
                        category = category,
                        capacity = capacity,
                        endTime = selectedEndTime,
                        eventType = eventType,
                        isPrivate = isPrivate,
                        requireDni = requireDni,
                        isUntransferable = false,
                        accessCode = "", // El repositorio lo autogenera si isPrivate es true
                        latitude = if (isPresencial || isHibrido) selectedLatLng?.latitude else null,
                        longitude = if (isPresencial || isHibrido) selectedLatLng?.longitude else null,
                        imageUrl = imageUrl
                    )
                    binding.btnPublish.isEnabled = true

                    if (eventId == -1L) {
                        showMessage("No se pudo crear el evento")
                        return@launch
                    }

                    showMessage("Evento publicado correctamente")
                    closeForm()
                }
            }
        }
    }

    private fun setupEventTypeToggle() {
        binding.toggleEventType.check(R.id.btnPresencial)
        binding.toggleEventType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnPresencial -> {
                        binding.layoutPresencialFields.visibility = View.VISIBLE
                        binding.layoutVirtualFields.visibility = View.GONE
                    }
                    R.id.btnVirtual -> {
                        binding.layoutPresencialFields.visibility = View.GONE
                        binding.layoutVirtualFields.visibility = View.VISIBLE
                    }
                    R.id.btnHibrido -> {
                        binding.layoutPresencialFields.visibility = View.VISIBLE
                        binding.layoutVirtualFields.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupVirtualPlatformSpinner() {
        val platforms = arrayOf("Zoom", "Google Meet", "YouTube Live", "Twitch", "Facebook Live", "Otro (Enlace externo)")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, platforms)
        binding.spPlatform.adapter = adapter

        binding.spPlatform.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = platforms[position]
                if (selected == "Zoom" || selected == "Google Meet") {
                    binding.tvMeetingPasswordLabel.visibility = View.VISIBLE
                    binding.etMeetingPassword.visibility = View.VISIBLE
                } else {
                    binding.tvMeetingPasswordLabel.visibility = View.GONE
                    binding.etMeetingPassword.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupPrivacyToggle() {
        binding.togglePrivacy.check(R.id.btnPublico)
        binding.togglePrivacy.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnPrivado) {
                    binding.etAccessCode.setText("(Se generará automáticamente al publicar)")
                    binding.etAccessCode.visibility = View.VISIBLE
                } else {
                    binding.etAccessCode.setText("")
                    binding.etAccessCode.visibility = View.GONE
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapPicker) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        binding.mapTouchOverlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    binding.root.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.root.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        binding.btnMyLocation.setOnClickListener {
            checkLocationPermission()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val defaultLatLng = LatLng(-16.4090, -71.5375) // Arequipa por defecto
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 14f))

        map?.setOnMapClickListener { latLng ->
            selectedLatLng = latLng
            marker?.remove()
            marker = map?.addMarker(MarkerOptions().position(latLng).title("Lugar del evento"))

            val coordsText = String.format(Locale.getDefault(), "Lat: %.4f, Lng: %.4f", latLng.latitude, latLng.longitude)
            binding.tvSelectedCoords.text = coordsText

            getAddressFromCoordinates(latLng)
        }
    }

    private fun getAddressFromCoordinates(latLng: LatLng) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0].getAddressLine(0)
                            launch(Dispatchers.Main) {
                                binding.etLocation.setText(address)
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0].getAddressLine(0)
                        withContext(Dispatchers.Main) {
                            binding.etLocation.setText(address)
                        }
                    }
                }
            } catch (e: Exception) {
                // Manejo silencioso si falla internet o el geocoder
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        map?.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))

                selectedLatLng = currentLatLng
                marker?.remove()
                marker = map?.addMarker(MarkerOptions().position(currentLatLng).title("Mi ubicación"))

                binding.tvSelectedCoords.text = String.format(Locale.getDefault(), "Lat: %.4f, Lng: %.4f", currentLatLng.latitude, currentLatLng.longitude)
                getAddressFromCoordinates(currentLatLng)
            } else {
                showMessage("No se pudo obtener la ubicación actual")
            }
        }
    }

    private fun showSelectedImage(uri: Uri) {
        selectedImageUri = uri
        binding.ivImagePreview.isVisible = true
        binding.layoutUploadPlaceholder.isVisible = false
        Glide.with(this).load(uri).centerCrop().into(binding.ivImagePreview)
    }

    private fun loadEventForEdit(userId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val event = eventRepository.getEventById(editEventId)
            if (event == null || event.creatorId != userId) {
                showMessage("No se pudo cargar el evento")
                closeForm()
                return@launch
            }
            editingEvent = event
            binding.tvFormTitle.text = "Editar evento"
            binding.etEventTitle.setText(event.title)
            binding.etEventDescription.setText(event.description)
            binding.etLocation.setText(event.location)
            binding.etCapacity.setText(event.capacity.toString())
            binding.cbRequireDni.isChecked = event.requireDni

            if (event.isPrivate) {
                binding.togglePrivacy.check(R.id.btnPrivado)
                binding.etAccessCode.setText(event.accessCode.orEmpty())
                binding.etAccessCode.visibility = View.VISIBLE
            }

            val imgUrl = event.imageUrl
            if (!imgUrl.isNullOrBlank()) {
                binding.ivImagePreview.isVisible = true
                binding.layoutUploadPlaceholder.isVisible = false
                Glide.with(this@EventFormFragment).load(imgUrl).centerCrop().into(binding.ivImagePreview)
            }
        }
    }

    private fun showDatePicker(etDate: EditText) {
        val calendar = Calendar.getInstance()
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDateTime.set(Calendar.YEAR, year)
                selectedDateTime.set(Calendar.MONTH, month)
                selectedDateTime.set(Calendar.DAY_OF_MONTH, day)
                val displayFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                etDate.setText(displayFormatter.format(selectedDateTime.time))
                dateSelected = true
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    private fun showTimePicker(etTime: EditText, isStart: Boolean) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                val formattedTime = String.format(Locale.getDefault(), "%02d:%02d hrs", hour, minute)
                etTime.setText(formattedTime)
                if (isStart) {
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, hour)
                    selectedDateTime.set(Calendar.MINUTE, minute)
                    timeSelected = true
                } else {
                    selectedEndTime = String.format(Locale.getDefault(), "%02d:%02d:00", hour, minute)
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun openLogin() {
        navigateTo(R.id.loginFragment)
    }

    private fun closeForm() {
        goBack()
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}