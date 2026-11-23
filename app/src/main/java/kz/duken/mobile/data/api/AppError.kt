package kz.duken.mobile.data.api

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** Ошибка, понятная экрану: что случилось и можно ли повторить. */
sealed class AppError(val retryable: Boolean) {
    /** Нет сети или сервер недоступен */
    data object Offline : AppError(retryable = true)

    /** Сервер не ответил вовремя */
    data object Timeout : AppError(retryable = true)

    /** Сессия истекла — нужно войти заново */
    data object Unauthorized : AppError(retryable = false)

    /** Ответ сервера с кодом и текстом для человека */
    data class Http(val status: Int, val code: String, val message: String) :
        AppError(retryable = status >= 500 || status == 429)

    data class Unknown(val description: String) : AppError(retryable = true)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun from(t: Throwable): AppError = when (t) {
            is SocketTimeoutException -> Timeout
            is HttpException -> fromHttp(t)
            is IOException -> Offline
            else -> Unknown(t.javaClass.simpleName)
        }

        private fun fromHttp(e: HttpException): AppError {
            val status = e.code()
            val raw = e.response()?.errorBody()?.string().orEmpty()
            val detail = runCatching { json.decodeFromString<ErrorBody>(raw).error }.getOrNull()
            return when {
                status == 401 && detail?.code != "bad_credentials" -> Unauthorized
                detail != null -> Http(status, detail.code, detail.message)
                // 422 от FastAPI без нашего формата: {"detail": [...]}
                status == 422 -> Http(status, "validation", "Проверьте введённые данные")
                else -> Http(status, "http_$status", "Ошибка сервера ($status)")
            }
        }
    }
}

/** Результат вызова API без исключений наружу. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Fail(val error: AppError) : ApiResult<Nothing>
}

suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Ok(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (t: Throwable) {
    ApiResult.Fail(AppError.from(t))
}
