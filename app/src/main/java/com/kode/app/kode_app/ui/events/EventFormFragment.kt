package com.kode.app.kode_app.ui.events

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.google.android.material.chip.Chip
import com.kode.app.kode_app.R
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.core.goToTab
import com.kode.app.kode_app.core.navigateTo
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.databinding.FragmentEventFormBinding
import com.kode.app.kode_app.ui.auth.LoginFragment
import com.kode.app.kode_app.util.Constantes
import kotlinx.coroutines.launch
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

        binding.btnBack.setOnClickListener { navigateToHome() }
        binding.btnCancel.setOnClickListener { navigateToHome() }
        binding.btnSaveDraft.setOnClickListener { showMessage("Borrador guardado") }

        setupMap()

        binding.btnPublish.setOnClickListener {
            val title = binding.etEventTitle.text.toString().trim()
            val description = binding.etEventDescription.text.toString().trim()
            val location = binding.etLocation.text.toString().trim()
            val capacityText = binding.etCapacity.text.toString().trim()
            val accessCode = binding.etAccessCode.text.toString().trim()

            val selectedChipId = binding.chipGroupCategory.checkedChipId
            if (selectedChipId == View.NO_ID) { showMessage("Selecciona una categoría"); return@setOnClickListener }
            val category = binding.root.findViewById<Chip>(selectedChipId).text.toString().replace("⚡", "").trim()


            val eventType = when (binding.toggleEventType.checkedButtonId) {
                R.id.btnVirtual -> "Virtual"
                R.id.btnHibrido -> "Híbrido"
                else -> "Presencial"
            }


            val isPrivate = binding.togglePrivacy.checkedButtonId == R.id.btnPrivado

            if (title.isEmpty()) { showMessage("Ingresa el nombre del evento"); return@setOnClickListener }
            if (description.isEmpty()) { showMessage("Ingresa una descripción"); return@setOnClickListener }
            if (location.isEmpty()) { showMessage("Ingresa la ubicación"); return@setOnClickListener }
            if (!dateSelected) { showMessage("Selecciona la fecha"); return@setOnClickListener }
            if (!timeSelected) { showMessage("Selecciona la hora de inicio"); return@setOnClickListener }

            val capacity = capacityText.toIntOrNull()
            if (capacity == null || capacity <= 0) {
                showMessage("Ingresa una capacidad válida")
                return@setOnClickListener
            }

            if (selectedDateTime.timeInMillis <= System.currentTimeMillis()) {
                showMessage("La fecha del evento debe ser futura")
                return@setOnClickListener
            }

            val creatorId = session.getUserId()
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val databaseDate = formatter.format(selectedDateTime.time)

            val requireDni = binding.cbRequireDni.isChecked
            val isUntransferable = binding.cbUntransferable.isChecked
            val imageUrl = binding.etImageUrl.text.toString()

            binding.btnPublish.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
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
                    isUntransferable = isUntransferable,
                    accessCode = accessCode,
                    latitude = selectedLatLng?.latitude,
                    longitude = selectedLatLng?.longitude,
                    imageUrl = imageUrl
                )
                binding.btnPublish.isEnabled = true

                if (eventId == -1L) {
                    showMessage("No se pudo crear el evento")
                    return@launch
                }

                showMessage("Evento publicado correctamente")
                navigateToHome()
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
                    selectedDateTime.set(Calendar.SECOND, 0)
                    selectedDateTime.set(Calendar.MILLISECOND, 0)
                    timeSelected = true
                } else {
                    selectedEndTime = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMap() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        val mapFragment = childFragmentManager
            .findFragmentById(binding.mapPicker.id) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.mapTouchOverlay.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                view.parent.requestDisallowInterceptTouchEvent(true)
            }
            false
        }

        binding.btnMyLocation.setOnClickListener { checkLocationPermission() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isZoomControlsEnabled = true

        val start = selectedLatLng ?: LatLng(Constantes.LIMA_LAT, Constantes.LIMA_LNG)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(start, if (selectedLatLng != null) 16f else 11f))
        selectedLatLng?.let { selectLocation(it) }

        googleMap.setOnMapClickListener { latLng -> selectLocation(latLng) }
    }

    private fun selectLocation(latLng: LatLng) {
        selectedLatLng = latLng
        marker?.remove()
        marker = map?.addMarker(MarkerOptions().position(latLng).title("Lugar del evento"))
        _binding?.tvSelectedCoords?.text = String.format(
            Locale.US, "📍 %.5f, %.5f", latLng.latitude, latLng.longitude
        )
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        val googleMap = map ?: return
        googleMap.isMyLocationEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentLocation = LatLng(location.latitude, location.longitude)
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16f))
                selectLocation(currentLocation)
            } else {
                showMessage("No se puede obtener la ubicación actual")
            }
        }
    }

    private fun navigateToHome() {
        goToTab(R.id.nav_home)
    }

    private fun openLogin() {
        navigateTo(R.id.loginFragment, LoginFragment.args(destination = "CREATE_EVENT"), replaceCurrent = true)
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}