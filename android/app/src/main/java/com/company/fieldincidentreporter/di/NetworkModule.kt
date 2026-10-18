package com.company.fieldincidentreporter.di

import com.company.fieldincidentreporter.BuildConfig
import com.company.fieldincidentreporter.data.IncidentApi
import com.company.fieldincidentreporter.data.IncidentRepository
import com.company.fieldincidentreporter.data.DraftPhotoStore
import com.company.fieldincidentreporter.data.NetworkIncidentRepository
import com.company.fieldincidentreporter.data.PhotoStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun client(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient, json: Json): IncidentApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(IncidentApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindIncidentRepository(implementation: NetworkIncidentRepository): IncidentRepository

    @Binds
    abstract fun bindDraftPhotoStore(implementation: PhotoStorage): DraftPhotoStore
}
