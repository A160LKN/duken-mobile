package kz.duken.mobile.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.duken.mobile.R
import kz.duken.mobile.data.local.CartItem
import kz.duken.mobile.ui.components.EmptyState
import kz.duken.mobile.ui.components.Loading
import kz.duken.mobile.ui.components.errorText
import kz.duken.mobile.util.formatTenge

@Composable
fun CartScreen(vm: CartViewModel, onCheckout: (orderId: Int) -> Unit, onCatalog: () -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val quote by vm.quote.collectAsStateWithLifecycle()
    var promoInput by rememberSaveable { mutableStateOf("") }
    val appliedPromo by vm.appliedPromo.collectAsStateWithLifecycle()

    val list = items
    LaunchedEffect(list, appliedPromo) {
        if (list != null) vm.requestQuote(list, appliedPromo)
    }

    when {
        list == null -> Loading()
        list.isEmpty() -> EmptyState(stringResource(R.string.cart_empty), stringResource(R.string.cart_empty_hint), "cart_empty") {
            OutlinedButton(onClick = onCatalog, modifier = Modifier.testTag("to_catalog_button")) {
                Text(stringResource(R.string.to_catalog))
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).testTag("cart_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.productId }) { item -> CartRow(item, vm) }
            }
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = promoInput,
                            onValueChange = { promoInput = it.uppercase(); vm.clearPromoError() },
                            label = { Text(stringResource(R.string.promo_hint)) },
                            singleLine = true,
                            isError = quote.promoError != null,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f).testTag("promo_input"),
                        )
                        OutlinedButton(
                            onClick = { vm.applyPromo(promoInput) },
                            modifier = Modifier.testTag("apply_promo_button"),
                        ) { Text(stringResource(R.string.apply)) }
                    }
                    quote.promoError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("promo_error"))
                    }
                    Totals(quote)
                    quote.checkoutError?.let {
                        Text(errorText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("checkout_error"))
                    }
                    Button(
                        onClick = { vm.checkout(onCheckout) },
                        enabled = !quote.checkingOut && quote.quote != null && !quote.loading,
                        modifier = Modifier.fillMaxWidth().testTag("checkout_button"),
                    ) {
                        if (quote.checkingOut) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.checkout))
                    }
                }
            }
        }
    }
}

@Composable
private fun Totals(state: QuoteState) {
    val q = state.quote
    when {
        state.error != null -> Text(errorText(state.error), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("quote_error"))
        q == null -> Text("…", Modifier.testTag("quote_loading"))
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TotalLine(stringResource(R.string.subtotal), formatTenge(q.subtotal), "subtotal")
            if (q.discount > 0) {
                TotalLine(stringResource(R.string.discount) + (q.promoCode?.let { " · $it" } ?: ""),
                    "−" + formatTenge(q.discount), "discount")
            }
            HorizontalDivider()
            TotalLine(stringResource(R.string.total), formatTenge(q.total), "total", bold = true)
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, tag: String, bold: Boolean = false) {
    val style = if (bold) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = style)
        Spacer(Modifier.weight(1f))
        Text(value, style = style, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun CartRow(item: CartItem, vm: CartViewModel) {
    Card(Modifier.fillMaxWidth().testTag("cart_item_${item.productId}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("cart_item_name"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTenge(item.price * item.quantity), Modifier.weight(1f).testTag("cart_item_sum_${item.productId}"))
                val dec = stringResource(R.string.decrease)
                val inc = stringResource(R.string.increase)
                val rem = stringResource(R.string.remove)
                IconButton(onClick = { vm.setQuantity(item, item.quantity - 1) },
                    modifier = Modifier.testTag("qty_minus_${item.productId}").semantics { contentDescription = "$dec: ${item.name}" }) {
                    Icon(Icons.Filled.Remove, contentDescription = null)
                }
                Text("${item.quantity}", Modifier.widthIn(min = 28.dp).testTag("qty_value_${item.productId}"),
                    style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.setQuantity(item, item.quantity + 1) }, enabled = item.quantity < item.stock,
                    modifier = Modifier.testTag("qty_plus_${item.productId}").semantics { contentDescription = "$inc: ${item.name}" }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
                IconButton(onClick = { vm.remove(item) },
                    modifier = Modifier.testTag("remove_${item.productId}").semantics { contentDescription = "$rem: ${item.name}" }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                }
            }
        }
    }
}
