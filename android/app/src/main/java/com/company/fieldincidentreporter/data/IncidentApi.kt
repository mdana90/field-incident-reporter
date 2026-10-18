package com.company.fieldincidentreporter.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path

interface IncidentApi {
    @GET("incidents")
    suspend fun list(): List<IncidentDto>

    @GET("incidents/{id}")
    suspend fun get(@Path("id") id: String): IncidentDto

    @Multipart
    @POST("incidents")
    suspend fun create(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part photos: List<MultipartBody.Part>,
    ): IncidentDto

    @Multipart
    @PUT("incidents/{id}")
    suspend fun update(
        @Path("id") id: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part photos: List<MultipartBody.Part>,
    ): IncidentDto

    @DELETE("incidents/{id}")
    suspend fun delete(@Path("id") id: String)
}
