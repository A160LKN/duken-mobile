package kz.duken.mobile.data.api

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * @param tokenProvider текущий токен или null
     * @param onUnauthorized вызывается, когда сервер ответил 401 на запрос с токеном
     */
    fun create(baseUrl: String, tokenProvider: () -> String?, onUnauthorized: () -> Unit): ShopApi {
        val auth = Interceptor { chain ->
            val token = tokenProvider()
            val request = chain.request().newBuilder().apply {
                if (token != null) header("Authorization", "Bearer $token")
            }.build()
            val response = chain.proceed(request)
            if (response.code == 401 && token != null) onUnauthorized()
            response
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // Оплата ждёт шлюз до 5 секунд, поэтому чтение ответа — с запасом
            .readTimeout(15, TimeUnit.SECONDS)
            // Без тихих повторов: OkHttp иначе сам переотправляет POST после обрыва соединения,
            // в том числе оплату. Повторять ли — решает пользователь кнопкой
            .retryOnConnectionFailure(false)
            .addInterceptor(auth)
            .build()
        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ShopApi::class.java)
    }
}
