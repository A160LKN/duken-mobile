package kz.duken.mobile.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kz.duken.mobile.R
import kz.duken.mobile.data.api.ApiResult
import kz.duken.mobile.data.api.AppError
import kz.duken.mobile.data.api.Product
import kz.duken.mobile.data.repo.CartRepository
import kz.duken.mobile.data.repo.CatalogRepository
import kz.duken.mobile.ui.components.ErrorState
import kz.duken.mobile.ui.components.Loading
import kz.duken.mobile.ui.components.categoryLabel
import kz.duken.mobile.util.formatTenge

sealed interface ProductState {
    data object Loading : ProductState
    data class Ready(val product: Product) : ProductState
    data class Failed(val error: AppError) : ProductState
}

class ProductViewModel(
    private val catalog: CatalogRepository,
    private val cart: CartRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    /** id из маршрута product/{id} — и из ссылки duken://product/{id} */
    private val id: Int = saved.get<String>("id")?.toIntOrNull() ?: saved.get<Int>("id") ?: -1

    val quantity: StateFlow<Int> = saved.getStateFlow(KEY_QTY, 1)

    private val _state = MutableStateFlow<ProductState>(ProductState.Loading)
    val state: StateFlow<ProductState> = _state.asStateFlow()

    init { load() }

    fun load() {
        if (id <= 0) {
            _state.value = ProductState.Failed(AppError.Http(404, "not_found", "Товар не найден"))
            return
        }
        _state.value = ProductState.Loading
        viewModelScope.launch {
            _state.value = when (val r = catalog.product(id)) {
                is ApiResult.Ok -> ProductState.Ready(r.value)
                is ApiResult.Fail -> ProductState.Failed(r.error)
            }
        }
    }

    fun changeQuantity(delta: Int) {
        val stock = (state.value as? ProductState.Ready)?.product?.stock ?: return
        saved[KEY_QTY] = (quantity.value + delta).coerceIn(1, maxOf(1, minOf(stock, CartRepository.MAX_PER_LINE)))
    }

    suspend fun addToCart(): Boolean {
        val product = (state.value as? ProductState.Ready)?.product ?: return false
        return cart.add(product, quantity.value)
    }

    private companion object { const val KEY_QTY = "quantity" }
}

@Composable
fun ProductScreen(vm: ProductViewModel, showMessage: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val quantity by vm.quantity.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    when (val s = state) {
        ProductState.Loading -> Loading()
        is ProductState.Failed -> ErrorState(s.error, onRetry = vm::load)
        is ProductState.Ready -> {
            val p = s.product
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AssistChip(onClick = {}, label = { Text(categoryLabel(p.category)) })
                Text(p.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("product_title"))
                Text(formatTenge(p.price), style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("product_detail_price"))
                Text(
                    if (p.stock > 0) stringResource(R.string.in_stock, p.stock) else stringResource(R.string.out_of_stock),
                    modifier = Modifier.testTag("product_detail_stock"),
                )
                if (p.stock > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.quantity))
                        OutlinedIconButton(onClick = { vm.changeQuantity(-1) }, enabled = quantity > 1,
                            modifier = Modifier.testTag("quantity_minus")) {
                            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.decrease))
                        }
                        Text("$quantity", Modifier.widthIn(min = 32.dp).testTag("quantity_value"), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium)
                        OutlinedIconButton(onClick = { vm.changeQuantity(1) }, enabled = quantity < p.stock,
                            modifier = Modifier.testTag("quantity_plus")) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase))
                        }
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            val added = vm.addToCart()
                            showMessage(context.getString(if (added) R.string.added_to_cart else R.string.stock_limit))
                        }
                    },
                    enabled = p.stock > 0,
                    modifier = Modifier.fillMaxWidth().testTag("add_to_cart_button"),
                ) { Text(stringResource(R.string.add_to_cart)) }
            }
        }
    }
}
