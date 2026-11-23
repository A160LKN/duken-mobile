package kz.duken.mobile.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import kz.duken.mobile.ui.components.EmptyState
import kz.duken.mobile.ui.components.ErrorState
import kz.duken.mobile.ui.components.Loading
import kz.duken.mobile.ui.components.errorText
import kz.duken.mobile.ui.components.statusLabel
import kz.duken.mobile.util.formatTenge

data class OrdersState(
    val orders: List<Order>? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val actionError: AppError? = null,
    val busy: Boolean = false,
)

class OrdersViewModel(private val repo: OrderRepository) : ViewModel() {
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.list()) {
                is ApiResult.Ok -> _state.update { it.copy(orders = r.value.sortedByDescending { o -> o.id }, loading = false) }
                is ApiResult.Fail -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }
}

class OrderDetailViewModel(private val repo: OrderRepository, saved: SavedStateHandle) : ViewModel() {
    private val id: Int = saved.get<String>("id")?.toIntOrNull() ?: saved.get<Int>("id") ?: -1
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()
    val order get() = _state.value.orders?.firstOrNull()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.get(id)) {
                is ApiResult.Ok -> _state.update { it.copy(orders = listOf(r.value), loading = false) }
                is ApiResult.Fail -> _state.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun cancel() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            when (val r = repo.cancel(id)) {
                is ApiResult.Ok -> _state.update { it.copy(orders = listOf(r.value), busy = false) }
                is ApiResult.Fail -> _state.update { it.copy(busy = false, actionError = r.error) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(vm: OrdersViewModel, onOpen: (Int) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Список обновляется при каждом открытии вкладки: статус мог смениться
    LaunchedEffect(Unit) { vm.load() }
    val orders = state.orders
    when {
        orders == null && state.loading -> Loading()
        orders == null && state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
        orders.isNullOrEmpty() -> EmptyState(stringResource(R.string.orders_empty), null, "orders_empty")
        else -> PullToRefreshBox(isRefreshing = state.loading, onRefresh = vm::load) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("orders_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(orders, key = { it.id }) { order ->
                    Card(Modifier.fillMaxWidth().testTag("order_row_${order.id}").clickable { onOpen(order.id) }) {
                        Row(Modifier.padding(16.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.order_number, order.id), style = MaterialTheme.typography.titleMedium)
                                Text(statusLabel(order.status), Modifier.testTag("order_status_${order.id}"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatTenge(order.total), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailScreen(vm: OrderDetailViewModel, onPay: (orderId: Int) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    // При каждом возвращении на экран (например, с экрана успешной оплаты) — свежий статус заказа
    LaunchedEffect(Unit) { vm.load() }
    val order = state.orders?.firstOrNull()
    when {
        order == null && state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
        order == null -> Loading()
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.order_number, order.id), style = MaterialTheme.typography.headlineSmall)
            Text(statusLabel(order.status), Modifier.testTag("order_detail_status"), style = MaterialTheme.typography.titleMedium)
            order.items.forEach { line ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${line.name} × ${line.quantity}", Modifier.weight(1f))
                    Text(formatTenge(line.price * line.quantity))
                }
            }
            HorizontalDivider()
            if (order.discount > 0) {
                Row { Text(stringResource(R.string.discount)); Spacer(Modifier.weight(1f)); Text("−" + formatTenge(order.discount)) }
            }
            Row {
                Text(stringResource(R.string.total), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(formatTenge(order.total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("order_detail_total"))
            }
            state.actionError?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("order_action_error")) }
            if (order.status == "created") {
                Button(onClick = { onPay(order.id) }, modifier = Modifier.fillMaxWidth().testTag("pay_order_button")) {
                    Text(stringResource(R.string.pay_order))
                }
            }
            if (order.status == "created" || order.status == "paid") {
                OutlinedButton(onClick = vm::cancel, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("cancel_order_button")) {
                    Text(stringResource(R.string.cancel_order))
                }
            }
        }
    }
}
