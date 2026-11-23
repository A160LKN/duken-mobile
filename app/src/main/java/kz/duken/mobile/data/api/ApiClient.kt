package kz.duken.mobile.data.api

import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /**
     * Два клиента с общим адресом и авторизацией.
     *
     * @param safeToRetry true — чтение и запросы, которые можно повторить без последствий. OkHttp сам повторяет
     *   запрос, если соединение из пула оказалось закрыто сервером (uvicorn закрывает простаивающие через 5 с).
     *   false — запросы, повтор которых опасен (оплата, создание заказа, отмена, регистрация): без автоповтора
     *   и без пула соединений — у такого клиента не бывает устаревших соединений, а повтор после обрыва
     *   решает пользователь.
     * @param tokenProvider текущий токен или null
     * @param onUnauthorized вызывается, когда сервер ответил 401 на запрос с токеном
     */
    fun create(
        baseUrl: String,
        safeToRetry: Boolean,
        tokenProvider: () -> String?,
        onUnauthorized: () -> Unit,
    ): ShopApi {
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
            .retryOnConnectionFailure(safeToRetry)
            .apply { if (!safeToRetry) connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS)) }
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
