package kz.duken.mobile

import kotlinx.coroutines.test.runTest
import kz.duken.mobile.data.api.ApiClient
import kz.duken.mobile.data.api.ApiResult
import kz.duken.mobile.data.api.AppError
import kz.duken.mobile.data.api.Credentials
import kz.duken.mobile.data.api.apiCall
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Как ответы сервера превращаются в ошибки для экрана. Настоящий HTTP, без устройства. */
class ApiErrorTest {
    private val server = MockWebServer()
    private var token: String? = null
    private var unauthorizedCalls = 0

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun api() = ApiClient.create(server.url("/").toString(), { token }) { unauthorizedCalls++ }

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    @Test fun businessErrorKeepsServerMessage() = runTest {
        server.enqueue(json(409, """{"error":{"code":"out_of_stock","message":"На складе 0 шт."}}"""))
        val r = apiCall { api().products() }
        assertEquals(ApiResult.Fail(AppError.Http(409, "out_of_stock", "На складе 0 шт.")), r)
    }

    @Test fun badCredentialsIsNotSessionExpiry() = runTest {
        server.enqueue(json(401, """{"error":{"code":"bad_credentials","message":"Неверный email или пароль"}}"""))
        val r = apiCall { api().login(Credentials("a@b.kz", "12345678")) } as ApiResult.Fail
        assertEquals("bad_credentials", (r.error as AppError.Http).code)
        assertEquals(0, unauthorizedCalls)
    }

    @Test fun rejectedTokenReportsSessionExpiry() = runTest {
        token = "old"
        server.enqueue(json(401, """{"detail":"Not authenticated"}"""))
        val r = apiCall { api().orders() } as ApiResult.Fail
        assertEquals(AppError.Unauthorized, r.error)
        assertEquals(1, unauthorizedCalls)
    }

    @Test fun tokenSentAsBearer() = runTest {
        token = "abc"
        server.enqueue(json(200, "[]"))
        apiCall { api().orders() }
        assertEquals("Bearer abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun fastApiValidationErrorHasReadableText() = runTest {
        server.enqueue(json(422, """{"detail":[{"loc":["body","email"],"msg":"bad"}]}"""))
        val r = apiCall { api().login(Credentials("x", "y")) } as ApiResult.Fail
        assertEquals(AppError.Http(422, "validation", "Проверьте введённые данные"), r.error)
    }

    @Test fun droppedConnectionIsOffline() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val r = apiCall { api().products() } as ApiResult.Fail
        assertEquals(AppError.Offline, r.error)
        assertTrue(r.error.retryable)
    }

    @Test fun htmlFromProxyIsNotCrash() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"))
        val r = apiCall { api().products() } as ApiResult.Fail
        assertEquals(AppError.Http(502, "http_502", "Ошибка сервера (502)"), r.error)
        assertTrue(r.error.retryable)
    }

    @Test fun unknownFieldsInResponseAreIgnored() = runTest {
        server.enqueue(json(200, """{"items":[],"total":0,"limit":20,"offset":0,"next":"new-field"}"""))
        val r = apiCall { api().products() }
        assertTrue(r is ApiResult.Ok)
    }
}
