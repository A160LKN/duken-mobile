package kz.duken.mobile

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kz.duken.mobile.data.api.ApiClient
import kz.duken.mobile.data.api.ShopApi
import kz.duken.mobile.data.local.CartDatabase
import kz.duken.mobile.data.local.Session
import kz.duken.mobile.data.local.SessionStore
import kz.duken.mobile.data.repo.AuthRepository
import kz.duken.mobile.data.repo.CartRepository
import kz.duken.mobile.data.repo.CatalogRepository
import kz.duken.mobile.data.repo.OrderRepository

/** Ручное внедрение зависимостей: один экземпляр на процесс. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val sessionStore = SessionStore(context)
    private val database = CartDatabase.create(context)

    @Volatile
    private var token: String? = runBlocking { sessionStore.current()?.token }

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Сервер отклонил токен: экран входа показывает «сессия истекла» */
    val sessionExpired = _sessionExpired.asSharedFlow()

    @Volatile
    private var api: ShopApi = build(baseUrl())

    init {
        scope.launch { sessionStore.session.collect { token = it?.token } }
    }

    fun baseUrl(): String = prefs.getString(KEY_BASE_URL, null) ?: BuildConfig.API_BASE_URL

    /** Только для debug-сборки: автотесты направляют приложение на свой сервер */
    fun overrideBaseUrl(url: String) {
        if (!BuildConfig.TEST_HOOKS || url == baseUrl()) return
        prefs.edit().putString(KEY_BASE_URL, url).apply()
        api = build(url)
    }

    /** Только для debug-сборки: сессия из автотеста, без экрана входа */
    fun signInForTests(token: String, email: String) {
        if (!BuildConfig.TEST_HOOKS) return
        this.token = token
        runBlocking { sessionStore.save(Session(token, email)) }
    }

    private fun build(url: String) = ApiClient.create(url, { token }) {
        token = null
        scope.launch { sessionStore.clear() }
        _sessionExpired.tryEmit(Unit)
    }

    val auth = AuthRepository({ api }, sessionStore)
    val catalog = CatalogRepository { api }
    val cart = CartRepository(database.cart())
    val orders = OrderRepository { api }

    private companion object {
        const val KEY_BASE_URL = "api_base_url"
    }
}
