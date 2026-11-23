package kz.duken.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kz.duken.mobile.R
import kz.duken.mobile.data.api.AppError

/** Текст ошибки для человека. Тексты бизнес-ошибок приходят с сервера уже на русском. */
@Composable
fun errorText(error: AppError): String = when (error) {
    AppError.Offline -> stringResource(R.string.error_offline)
    AppError.Timeout -> stringResource(R.string.error_timeout)
    AppError.Unauthorized -> stringResource(R.string.session_expired)
    is AppError.Http -> error.message
    is AppError.Unknown -> stringResource(R.string.error_unknown)
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag("loading"), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Ошибка во весь экран с кнопкой «Повторить» */
@Composable
fun ErrorState(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp).testTag("error_state"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            errorText(error),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("error_text"),
        )
        if (error.retryable) {
            Button(onClick = onRetry, modifier = Modifier.testTag("retry_button")) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
fun EmptyState(title: String, hint: String?, tag: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
fun categoryLabel(name: String): String = when (name) {
    "electronics" -> stringResource(R.string.category_electronics)
    "home" -> stringResource(R.string.category_home)
    "accessories" -> stringResource(R.string.category_accessories)
    "food" -> stringResource(R.string.category_food)
    else -> name
}

@Composable
fun statusLabel(status: String): String = when (status) {
    "created" -> stringResource(R.string.status_created)
    "paid" -> stringResource(R.string.status_paid)
    "shipped" -> stringResource(R.string.status_shipped)
    "delivered" -> stringResource(R.string.status_delivered)
    "cancelled" -> stringResource(R.string.status_cancelled)
    else -> status
}
