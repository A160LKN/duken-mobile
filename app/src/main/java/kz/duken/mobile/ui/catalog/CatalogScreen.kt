package kz.duken.mobile.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kz.duken.mobile.R
import kz.duken.mobile.data.api.Product
import kz.duken.mobile.ui.components.EmptyState
import kz.duken.mobile.ui.components.ErrorState
import kz.duken.mobile.ui.components.Loading
import kz.duken.mobile.ui.components.categoryLabel
import kz.duken.mobile.ui.components.errorText
import kz.duken.mobile.util.formatTenge

private val SORTS = listOf(
    "default" to R.string.sort_default,
    "price_asc" to R.string.sort_price_asc,
    "price_desc" to R.string.sort_price_desc,
    "name" to R.string.sort_name,
    "newest" to R.string.sort_newest,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(vm: CatalogViewModel, onOpen: (Int) -> Unit, showMessage: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val add: (Product) -> Unit = { product ->
        scope.launch {
            val added = vm.addToCart(product)
            showMessage(context.getString(if (added) R.string.added_to_cart else R.string.stock_limit))
        }
    }

    state.refreshError?.let { error ->
        val text = errorText(error)
        LaunchedEffect(error) { showMessage(text); vm.refreshErrorShown() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { vm.onQuery("") }, modifier = Modifier.testTag("clear_search")) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("search_input"),
            )
            SortMenu(sort, vm::onSort)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("category_chips"),
        ) {
            item {
                FilterChip(
                    selected = category == null,
                    onClick = { vm.onCategory(null) },
                    label = { Text(stringResource(R.string.all_categories)) },
                    modifier = Modifier.testTag("category_all"),
                )
            }
            items(state.categories, key = { it.name }) { c ->
                FilterChip(
                    selected = category == c.name,
                    onClick = { vm.onCategory(if (category == c.name) null else c.name) },
                    label = { Text("${categoryLabel(c.name)} · ${c.count}") },
                    modifier = Modifier.testTag("category_${c.name}"),
                )
            }
        }
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.reload(refresh = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.error != null -> ErrorState(state.error!!, onRetry = { vm.reload() })
                state.loading && state.items.isEmpty() -> Loading()
                state.items.isEmpty() -> EmptyState(
                    stringResource(R.string.nothing_found), stringResource(R.string.nothing_found_hint), "catalog_empty",
                )
                else -> ProductList(state, onOpen, add, vm::loadMore, vm::retryMore)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SortMenu(sort: String, onSort: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("sort_button")) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort))
        }
        // Меню — отдельное окно со своим корнем Compose: testTag → resource-id включается и здесь
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
        ) {
            SORTS.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label) + if (key == sort) "  ✓" else "") },
                    onClick = { onSort(key); open = false },
                    modifier = Modifier.testTag("sort_$key"),
                )
            }
        }
    }
}

@Composable
private fun ProductList(
    state: CatalogState,
    onOpen: (Int) -> Unit,
    onAdd: (Product) -> Unit,
    loadMore: () -> Unit,
    retryMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Новые фильтры — новый список с начала. Без этого LazyColumn держится за первый видимый товар по ключу,
    // и после сортировки экран оказывается в конце списка. rememberSaveable: поворот экрана позицию не сбрасывает
    var seenGeneration by rememberSaveable { mutableIntStateOf(state.generation) }
    LaunchedEffect(state.generation) {
        if (state.generation != seenGeneration) {
            listState.scrollToItem(0)
            seenGeneration = state.generation
        }
    }
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) loadMore() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("product_list"),
    ) {
        items(state.items, key = { it.id }) { product -> ProductCard(product, onOpen, onAdd) }
        when {
            state.loadingMore -> item {
                Box(Modifier.fillMaxWidth().padding(8.dp).testTag("load_more_spinner"), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.moreError != null -> item {
                Row(Modifier.fillMaxWidth().testTag("load_more_error"), verticalAlignment = Alignment.CenterVertically) {
                    Text(errorText(state.moreError), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = retryMore, modifier = Modifier.testTag("load_more_retry")) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            state.items.size >= state.total -> item {
                Text(
                    stringResource(R.string.items_count, state.total),
                    Modifier.fillMaxWidth().padding(8.dp).testTag("list_end"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onOpen: (Int) -> Unit, onAdd: (Product) -> Unit) {
    val price = formatTenge(product.price)
    val stockText = if (product.stock > 0) stringResource(R.string.in_stock, product.stock) else stringResource(R.string.out_of_stock)
    Card(
        Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}")
            .clickable { onOpen(product.id) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("product_name"))
                Text(price, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("product_price"))
                Text(stockText, style = MaterialTheme.typography.bodySmall,
                    color = if (product.stock > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("product_stock"))
            }
            val addLabel = stringResource(R.string.add_to_cart)
            FilledTonalIconButton(
                onClick = { onAdd(product) },
                enabled = product.stock > 0,
                modifier = Modifier.testTag("add_to_cart_${product.id}").semantics { contentDescription = "$addLabel: ${product.name}" },
            ) {
                Icon(Icons.Filled.AddShoppingCart, contentDescription = null)
            }
        }
    }
}
