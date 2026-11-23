package kz.duken.mobile.ui.profile

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import kz.duken.mobile.BuildConfig
import kz.duken.mobile.R

@Composable
fun ProfileScreen(email: String, serverUrl: String, onLogout: () -> Unit) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { "ru" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(email, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("profile_email"))
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ru" to R.string.language_ru, "kk" to R.string.language_kk).forEach { (tag, label) ->
                FilterChip(
                    selected = current.startsWith(tag),
                    // Язык приложения меняется без смены языка системы (Android 13+ и AppCompat ниже)
                    onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.testTag("language_$tag"),
                )
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().testTag("logout_button")) {
            Text(stringResource(R.string.logout))
        }
        Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("app_version"))
        if (BuildConfig.TEST_HOOKS) {
            Text(stringResource(R.string.server, serverUrl), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("server_url"))
        }
    }
}
