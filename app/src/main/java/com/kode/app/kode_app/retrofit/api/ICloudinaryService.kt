package com.kode.app.kode_app.retrofit.api

import com.kode.app.kode_app.retrofit.response.CloudinaryResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ICloudinaryService {
    @Multipart
    @POST("{cloudName}/image/upload")
    suspend fun subirImagen(
        @Path("cloudName") cloudName: String,
        @Part file: MultipartBody.Part,
        @Part("upload_preset") uploadPreset: RequestBody,
        @Part("folder") folder: RequestBody
    ): CloudinaryResponse
}
