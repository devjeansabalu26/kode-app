package com.kode.app.kode_app.ui.events

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.graphics.Canvas
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.kode.app.kode_app.core.goBack
import com.kode.app.kode_app.core.SessionManager
import com.kode.app.kode_app.data.EventRepository
import com.kode.app.kode_app.data.RegistrationRepository
import com.kode.app.kode_app.data.UserRepository
import com.kode.app.kode_app.databinding.FragmentTicketBinding
import kotlinx.coroutines.launch

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

        val eventRepository = EventRepository()
        val userRepository = UserRepository()
        val sessionManager = SessionManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            val event = eventRepository.getEventById(eventId)
            val user = sessionManager.getUserId().let { userRepository.getUserById(it) }

            if (event != null && user != null) {
                binding.tvTicketCategory.text = event.category.uppercase()
                binding.tvTicketEvent.text = if (eventTitle.isNotEmpty()) eventTitle else event.title
                binding.tvTicketUser.text = user.name

                val finalCode = qrCodeParam.ifEmpty {
                    RegistrationRepository().getRegistrationQr(event.id, user.id).orEmpty()
                }
                if (finalCode.isNotEmpty()) {
                    binding.tvTicketCode.text = "Acceso General • ID: #$finalCode"
                    binding.ivTicketQR.setImageBitmap(generateQrBitmap(finalCode))
                } else {
                    binding.tvTicketCode.text = "Aún no estás inscrito en este evento"
                }

                binding.tvTicketDate.text = event.date
                binding.tvTicketTime.text = if (!event.endTime.isNullOrEmpty()) event.endTime else "Hora fijada"
            }
        }

        binding.btnBack.setOnClickListener { goBack() }

        binding.btnShareTicket.setOnClickListener {
            downloadTicketAsImage(binding.cardTicketContainer)
        }
    }
    private fun downloadTicketAsImage(view: View) {
        try {

            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)


            val filename = "Boleto_KODE_${System.currentTimeMillis()}.png"
            var imageUri: android.net.Uri? = null
            val resolver = requireContext().contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KODE")
                }
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                imageUri?.let { uri ->
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }
            } else {

                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val kodeDir = java.io.File(imagesDir, "KODE")
                if (!kodeDir.exists()) kodeDir.mkdirs()

                val imageFile = java.io.File(kodeDir, filename)
                java.io.FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                imageUri = android.net.Uri.fromFile(imageFile)


                android.media.MediaScannerConnection.scanFile(
                    requireContext(),
                    arrayOf(imageFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
            }

            if (imageUri != null) {
                Toast.makeText(requireContext(), "¡Boleto guardado en Galeria", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Error al guardar el boleto", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateQrBitmap(text: String, size: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_EVENT_TITLE = "event_title"
        private const val ARG_QR_CODE = "qr_code"

        fun args(eventId: Int, eventTitle: String? = null, qrCode: String? = null): Bundle {
            return Bundle().apply {
                putInt(ARG_EVENT_ID, eventId)
                putString(ARG_EVENT_TITLE, eventTitle)
                putString(ARG_QR_CODE, qrCode)
            }
        }
    }
}