package com.kode.app.kode_app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.kode.app.kode_app.retrofit.ClienteCloudinaryRetrofit
import com.kode.app.kode_app.util.Constantes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class ImageRepository(private val context: Context) {
    private val cloudinary = ClienteCloudinaryRetrofit.retrofitCloudinaryService

    suspend fun uploadEventImage(uri: Uri): String? {
        return try {
            val bytes = withContext(Dispatchers.Default) { compress(uri) }
            val file = MultipartBody.Part.createFormData(
                "file", "evento.jpg", bytes.toRequestBody("image/jpeg".toMediaType())
            )
            val text = "text/plain".toMediaType()
            cloudinary.subirImagen(
                cloudName = Constantes.CLOUDINARY_CLOUD_NAME,
                file = file,
                uploadPreset = Constantes.CLOUDINARY_UPLOAD_PRESET.toRequestBody(text),
                folder = Constantes.CLOUDINARY_FOLDER.toRequestBody(text)
            ).secureUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Cloudinary", "Error al subir imagen", e)
            null
        }
    }

    private fun compress(uri: Uri): ByteArray {
        val original = decode(uri)
        val scale = minOf(1f, MAX_SIZE.toFloat() / maxOf(original.width, original.height))
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
        } else {
            original
        }
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            output.toByteArray()
        }
    }

    private fun decode(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    private companion object {
        const val MAX_SIZE = 1280
        const val JPEG_QUALITY = 80
    }
}
