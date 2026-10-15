package com.kode.app.kode_app.ui.checkin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.kode.app.kode_app.R
import com.kode.app.kode_app.data.RegistrationRepository
import com.kode.app.kode_app.data.RegistrationRepository.ScanResult
import com.kode.app.kode_app.databinding.FragmentQrScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QrScannerFragment : Fragment() {

    private var _binding: FragmentQrScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: RegistrationRepository
    private lateinit var cameraExecutor: ExecutorService

    private var eventId: Int = 1
    private var isProcessingScan = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Se requiere permiso de cámara para escanear QR", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = RegistrationRepository(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        arguments?.let {
            eventId = it.getInt("EVENT_ID", 1)
        }

        updateAttendanceMetrics()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnExpressRegister.setOnClickListener {
            showExpressRegisterDialog()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessingScan) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            val scanner = BarcodeScanning.getClient(options)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { qrValue ->
                            isProcessingScan = true
                            requireActivity().runOnUiThread {
                                handleQrCodeScanned(qrValue)
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun handleQrCodeScanned(qrCode: String) {
        when (val result = repository.processQrScan(eventId, qrCode)) {
            is ScanResult.Success -> {
                showScanDialog("✅ INGRESO PERMITIDO", "Bienvenido(a): ${result.userName}\nDNI: ${result.dni}")
            }
            is ScanResult.AlreadyCheckedIn -> {
                showScanDialog("⚠️ REINGRESO DUPLICADO", "El asistente ${result.userName} ya registró su ingreso a las ${result.checkinTime}.")
            }
            is ScanResult.NotFound -> {
                showNotFoundDialog(qrCode)
            }
            is ScanResult.Error -> {
                showScanDialog("❌ ERROR", result.message)
            }
        }
        updateAttendanceMetrics()
    }

    private fun showScanDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Siguiente Asistente") { dialog, _ ->
                dialog.dismiss()
                isProcessingScan = false
            }
            .show()
    }

    private fun showNotFoundDialog(qrCode: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("❓ REGISTRO NO ENCONTRADO")
            .setMessage("El código QR ($qrCode) no figura en la lista de preinscritos.\n\n¿Deseas realizar el Registro Express?")
            .setCancelable(false)
            .setPositiveButton("Registro Express") { dialog, _ ->
                dialog.dismiss()
                showExpressRegisterDialog()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                isProcessingScan = false
            }
            .show()
    }

    private fun showExpressRegisterDialog() {
        val context = requireContext()
        val layoutInflater = android.view.LayoutInflater.from(context)

        // Inflamos el layout usando el paquete completo para asegurar la referencia de R
        val dialogView = layoutInflater.inflate(com.kode.app.kode_app.R.layout.dialog_express_register, null)

        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.kode.app.kode_app.R.id.etExpressName)
        val etDni = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.kode.app.kode_app.R.id.etExpressDni)

        MaterialAlertDialogBuilder(context)
            .setTitle("Registro Express en Puerta")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Registrar e Ingresar") { dialogInterface: android.content.DialogInterface, _: Int ->
                val name = etName?.text?.toString()?.trim() ?: ""
                val dni = etDni?.text?.toString()?.trim() ?: ""

                if (name.isNotEmpty() && dni.length >= 8) {
                    val success = repository.registerExpressUser(eventId, name, dni)
                    if (success) {
                        Toast.makeText(context, "¡Registro express completado!", Toast.LENGTH_SHORT).show()
                        updateAttendanceMetrics()
                    } else {
                        Toast.makeText(context, "Error al registrar", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Ingrese datos válidos (DNI 8 dígitos)", Toast.LENGTH_SHORT).show()
                }
                dialogInterface.dismiss()
                isProcessingScan = false
            }
            .setNegativeButton("Cancelar") { dialogInterface: android.content.DialogInterface, _: Int ->
                dialogInterface.dismiss()
                isProcessingScan = false
            }
            .show()
    }

    private fun updateAttendanceMetrics() {
        val (attended, registered) = repository.getAttendanceCount(eventId)
        binding.tvAforoStats.text = "Aforo ingresado: $attended / $registered asistentes"
        if (registered > 0) {
            val progress = (attended.toFloat() / registered.toFloat() * 100).toInt()
            binding.progressAforo.progress = progress
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}