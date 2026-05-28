package com.aistudio.sharmakhata.pqmzvk.data.remote

import com.aistudio.sharmakhata.pqmzvk.data.exception.NetworkException
import com.aistudio.sharmakhata.pqmzvk.util.Constants
import com.aistudio.sharmakhata.pqmzvk.util.SessionManager
import com.aistudio.sharmakhata.pqmzvk.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val _unauthorizedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorizedEvent: SharedFlow<Unit> = _unauthorizedEvent.asSharedFlow()

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val key = BuildConfig.MOBILE_API_KEY
            val builder = chain.request().newBuilder()
            if (key.isNotBlank()) builder.addHeader("X-API-KEY", key)
            val token = SessionManager.token
            if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
            val req = builder.build()
            chain.proceed(req)
        }
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
                SessionManager.clearToken()
                _unauthorizedEvent.tryEmit(Unit)
            }
            response
        }
        .addInterceptor(loggingInterceptor)
        .addInterceptor(TimeoutInterceptor())
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(Constants.BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(httpClient)
        .build()

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}

/**
 * Custom interceptor to handle timeout scenarios
 */
class TimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        try {
            return chain.proceed(request)
        } catch (e: IOException) {
            // Wrap timeout exceptions with more descriptive messages
            throw NetworkException("Connection timeout. Please check your internet connection and try again.")
        }
    }
}
