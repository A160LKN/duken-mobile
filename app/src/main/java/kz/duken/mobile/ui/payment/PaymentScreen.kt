package kz.duken.mobile.ui.payment

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.duken.mobile.R
import kz.duken.mobile.data.api.ApiResult
import kz.duken.mobile.data.api.AppError
import kz.duken.mobile.data.api.Order
import kz.duken.mobile.data.repo.OrderRepository
import kz.duken.mobile.ui.components.ErrorState
import kz.duken.mobile.ui.components.Loading
import kz.duken.mobile.ui.components.errorText
import kz.duken.mobile.util.Notifications
import kz.duken.mobile.util.formatTenge

/** Тестовые карты: токены понимает имитация платёжного шлюза shop-api */
private val CARDS = listOf(
    "tok_visa" to R.string.card_ok,
    "tok_declined" to R.string.card_declined,
    "tok_timeout" to R.string.card_slow,
)

data class PaymentState(
    val order: Order? = null,
    val loadError: AppError? = null,
    val paying: Boolean = false,
    val error: AppError? = null,
    val paid: Order? = null,
)

/**
 * id заказа — из маршрута payment/{orderId}: он хранится в сохранённом состоянии навигации
 * и переживает гибель процесса. Сам заказ каждый раз читается с сервера — сумма и статус актуальные.
 */
class PaymentViewModel(private val orders: OrderRepository, saved: SavedStateHandle) : ViewModel() {
    internal val repo get() = orders
    private val orderId: Int = saved.get<String>("orderId")?.toIntOrNull() ?: saved.get<Int>("orderId") ?: -1

    private val _state = MutableStateFlow(PaymentState())
    val state: StateFlow<PaymentState> = _state.asStateFlow()
    val order: Order get() = _state.value.order!!

    init { load() }

    fun load() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            when (val r = orders.get(orderId)) {
                // Заказ уже оплачен (например, оплата прошла до гибели процесса) — сразу успех
                is ApiResult.Ok -> _state.update {
                    if (r.value.status == "paid") it.copy(order = r.value, paid = r.value) else it.copy(order = r.value)
                }
                is ApiResult.Fail -> _state.update { it.copy(loadError = r.error) }
            }
        }
    }

    fun pay(cardToken: String) {
        if (_state.value.paying || _state.value.order == null) return
        _state.update { it.copy(paying = true, error = null) }
        viewModelScope.launch {
            when (val r = orders.pay(order.id, cardToken)) {
                is ApiResult.Ok -> _state.update { it.copy(paying = false, paid = r.value) }
                is ApiResult.Fail -> {
                    val paid = alreadyPaid(r.error)
                    _state.update {
                        if (paid != null) it.copy(paying = false, paid = paid) else it.copy(paying = false, error = r.error)
                    }
                }
            }
        }
    }
}

/**
 * Прошлая попытка могла пройти, а ответ — потеряться в сети. Тогда повтор получает 409 «заказ уже оплачен».
 * Это не ошибка для покупателя: перечитываем заказ и, если он оплачен, показываем успех.
 */
private suspend fun PaymentViewModel.alreadyPaid(error: AppError): Order? {
    if (error !is AppError.Http || error.status != 409 || error.code != "invalid_status") return null
    val fresh = (repo.get(order.id) as? ApiResult.Ok)?.value ?: return null
    return fresh.takeIf { it.status == "paid" }
}

@Composable
fun PaymentScreen(vm: PaymentViewModel, onPaid: (Order) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var card by rememberSaveable { mutableStateOf(CARDS.first().first) }
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(state.paid) {
        val paid = state.paid ?: return@LaunchedEffect
        Notifications.orderPaid(context, paid.id, paid.total)
        // Разрешение на уведомления просим после первой оплаты, когда понятно, зачем оно
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onPaid(paid)
    }

    if (state.order == null) {
        state.loadError?.let { ErrorState(it, onRetry = vm::load) } ?: Loading()
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.payment_title, vm.order.id), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("payment_title"))
        Row {
            Text(stringResource(R.string.total), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(formatTenge(vm.order.total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("order_total"))
        }
        Text(stringResource(R.string.choose_card), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            CARDS.forEach { (token, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = card == token, onClick = { card = token }, role = Role.RadioButton, enabled = !state.paying)
                        .padding(vertical = 8.dp)
                        .testTag("card_$token"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = card == token, onClick = null)
                    Text(stringResource(label), Modifier.padding(start = 12.dp))
                }
            }
        }
        state.error?.let {
            Text(errorText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("payment_error"))
        }
        Button(
            onClick = { vm.pay(card) },
            enabled = !state.paying,
            modifier = Modifier.fillMaxWidth().testTag("pay_button"),
        ) {
            if (state.paying) {
                CircularProgressIndicator(Modifier.size(20.dp).testTag("payment_progress"), strokeWidth = 2.dp)
                Text(stringResource(R.string.paying), Modifier.padding(start = 12.dp))
            } else {
                Text(stringResource(R.string.pay, formatTenge(vm.order.total)))
            }
        }
    }
}

@Composable
fun PaymentSuccessScreen(orderId: Int, onOrders: () -> Unit, onCatalog: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.payment_success), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("success_title"))
        Text(stringResource(R.string.order_number, orderId), modifier = Modifier.testTag("success_order_id"))
        Button(onClick = onOrders, modifier = Modifier.fillMaxWidth().testTag("to_orders_button")) {
            Text(stringResource(R.string.to_orders))
        }
        OutlinedButton(onClick = onCatalog, modifier = Modifier.fillMaxWidth().testTag("continue_shopping_button")) {
            Text(stringResource(R.string.continue_shopping))
        }
    }
}
