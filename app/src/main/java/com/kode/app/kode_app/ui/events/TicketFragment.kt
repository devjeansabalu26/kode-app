package com.kode.app.kode_app.ui.events

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.kode.app.kode_app.R
import com.kode.app.kode_app.databinding.FragmentTicketBinding
import com.kode.app.kode_app.core.SessionManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.w3c.dom.Text

class TicketFragment :
    Fragment() {
    private var _binding: FragmentTicketBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTicketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = SessionManager(requireContext())
        val eventTitle = arguments?.getString(ARG_EVENT_TITLE) ?: ""
        val qrCode = arguments?.getString(ARG_QR_CODE) ?: ""
        binding.tvTicketUser.text = session.getName()
        binding.tvTicketEvent.text = eventTitle
        binding.tvTicketCode.text = qrCode
        val qrImage = binding.ivTicketQR
        qrImage.setImageBitmap(generateQRCode(qrCode))
    }

    private fun generateQRCode(text: String): Bitmap {
        val size = 512
        val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size){
            for (y in 0 until size){
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]){
                        Color.BLACK
                    } else {
                        Color.WHITE
                    }
                )
            }
        }
        return bitmap
    }

    companion object {
        private const val ARG_EVENT_ID = "event_id"
        private const val ARG_EVENT_TITLE = "event_title"
        private const val ARG_QR_CODE = "qr_code"
        fun newInstance(eventId: Int, eventTitle: String, qrCode: String): TicketFragment {
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