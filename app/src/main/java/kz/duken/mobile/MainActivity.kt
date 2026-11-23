package kz.duken.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import kz.duken.mobile.ui.DukenApp
import kz.duken.mobile.ui.theme.DukenTheme

class MainActivity : AppCompatActivity() {
    private val deepLinkProductId = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyTestHooks(intent)
        // После поворота экрана intent тот же — ссылку второй раз не открываем
        if (savedInstanceState == null) deepLinkProductId.value = productIdFrom(intent)
        setContent {
            DukenTheme {
                DukenApp(deepLinkProductId.value) { deepLinkProductId.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyTestHooks(intent)
        productIdFrom(intent)?.let { deepLinkProductId.value = it }
    }

    /**
     * Только debug-сборка. Автотесты передают при запуске:
     * - api_base_url — адрес тестового сервера;
     * - test_token и test_email — готовую сессию, чтобы не проходить вход в каждом тесте.
     */
    private fun applyTestHooks(intent: Intent?) {
        if (!BuildConfig.TEST_HOOKS || intent == null) return
        val container = (application as DukenApplication).container
        intent.getStringExtra("api_base_url")?.let(container::overrideBaseUrl)
        val token = intent.getStringExtra("test_token")
        val email = intent.getStringExtra("test_email")
        if (token != null && email != null) container.signInForTests(token, email)
    }

    private fun productIdFrom(intent: Intent?): Int? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "duken" || uri.host != "product") return null
        return uri.pathSegments.firstOrNull()?.toIntOrNull()?.takeIf { it > 0 }
    }
}
