package kz.duken.mobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kz.duken.mobile.R
import kz.duken.mobile.ui.auth.AuthScreen
import kz.duken.mobile.ui.auth.AuthViewModel
import kz.duken.mobile.ui.cart.CartScreen
import kz.duken.mobile.ui.cart.CartViewModel
import kz.duken.mobile.ui.catalog.CatalogScreen
import kz.duken.mobile.ui.catalog.CatalogViewModel
import kz.duken.mobile.ui.components.Loading
import kz.duken.mobile.ui.components.appViewModel
import kz.duken.mobile.ui.components.container
import kz.duken.mobile.ui.orders.OrderDetailScreen
import kz.duken.mobile.ui.orders.OrderDetailViewModel
import kz.duken.mobile.ui.orders.OrdersScreen
import kz.duken.mobile.ui.orders.OrdersViewModel
import kz.duken.mobile.ui.payment.PaymentScreen
import kz.duken.mobile.ui.payment.PaymentSuccessScreen
import kz.duken.mobile.ui.payment.PaymentViewModel
import kz.duken.mobile.ui.product.ProductScreen
import kz.duken.mobile.ui.product.ProductViewModel
import kz.duken.mobile.ui.profile.ProfileScreen

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab("catalog", R.string.nav_catalog, Icons.Filled.Storefront),
    Tab("cart", R.string.nav_cart, Icons.Filled.ShoppingCart),
    Tab("orders", R.string.nav_orders, Icons.AutoMirrored.Filled.ReceiptLong),
    Tab("profile", R.string.nav_profile, Icons.Filled.Person),
)

private val AUTH_ROUTES = setOf("login", "register")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DukenApp(deepLinkProductId: Int?, onDeepLinkHandled: () -> Unit) {
    val c = container()
    // null — ещё читаем сессию с диска; Optional-обёртка не нужна: пустой email не бывает
    val session by c.auth.session.collectAsStateWithLifecycle(initialValue = LOADING)
    val cartCount by c.cart.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sessionExpired by rememberSaveable { mutableStateOf(false) }
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    LaunchedEffect(Unit) { c.sessionExpired.collect { sessionExpired = true } }

    if (session === LOADING) {
        Loading(); return
    }
    val loggedIn = session != null

    // Вход и выход меняют весь граф: стек экранов очищается, «Назад» не возвращает на чужие экраны
    LaunchedEffect(loggedIn) {
        val current = nav.currentDestination?.route ?: return@LaunchedEffect
        if (loggedIn && current in AUTH_ROUTES) {
            sessionExpired = false
            nav.navigate("catalog") { popUpTo(0) { inclusive = true } }
        } else if (!loggedIn && current !in AUTH_ROUTES) {
            nav.navigate("login") { popUpTo(0) { inclusive = true } }
        }
    }
    LaunchedEffect(loggedIn, deepLinkProductId) {
        if (loggedIn && deepLinkProductId != null) {
            nav.navigate("product/$deepLinkProductId")
            onDeepLinkHandled()
        }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = TABS.any { it.route == route }

    Scaffold(
        // Compose testTag → resource-id: так элементы видят UiAutomator и Appium
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        snackbarHost = { SnackbarHost(snackbar, Modifier.testTag("snackbar")) },
        topBar = {
            if (route != null && route !in AUTH_ROUTES && !isTab) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.testTag("back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (isTab) BottomBar(nav, route, cartCount.sumOf { it.quantity })
        },
    ) { padding ->
        NavHost(nav, startDestination = if (loggedIn) "catalog" else "login", modifier = Modifier.padding(padding)) {
            composable("login") {
                AuthScreen(false, appViewModel { c, s -> AuthViewModel(c.auth, s) }, sessionExpired) {
                    nav.navigate("register") { popUpTo("login") { inclusive = true } }
                }
            }
            composable("register") {
                AuthScreen(true, appViewModel { c, s -> AuthViewModel(c.auth, s) }, false) {
                    nav.navigate("login") { popUpTo("register") { inclusive = true } }
                }
            }
            composable("catalog") {
                CatalogScreen(
                    appViewModel { c, s -> CatalogViewModel(c.catalog, c.cart, s) },
                    onOpen = { nav.navigate("product/$it") },
                    showMessage = showMessage,
                )
            }
            composable("product/{id}") {
                ProductScreen(appViewModel { c, s -> ProductViewModel(c.catalog, c.cart, s) }, showMessage)
            }
            composable("cart") {
                CartScreen(
                    appViewModel { c, s -> CartViewModel(c.cart, c.orders, s) },
                    onCheckout = { nav.navigate("payment/$it") },
                    onCatalog = { nav.switchTab("catalog") },
                )
            }
            composable("payment/{orderId}") {
                PaymentScreen(appViewModel { c, s -> PaymentViewModel(c.orders, s) }) { order ->
                    // Оплаченный заказ не оплачивают второй раз: экран оплаты уходит из стека,
                    // «Назад» с экрана успеха ведёт на вкладку, откуда начали оформление
                    nav.navigate("success/${order.id}") {
                        popUpTo("payment/{orderId}") { inclusive = true }
                    }
                }
            }
            composable("success/{id}") { e ->
                val id = e.arguments?.getString("id")?.toIntOrNull() ?: 0
                PaymentSuccessScreen(id, onOrders = { nav.leaveCheckout("orders") }, onCatalog = { nav.leaveCheckout("catalog") })
            }
            composable("orders") {
                OrdersScreen(appViewModel { c, _ -> OrdersViewModel(c.orders) }) { nav.navigate("order/$it") }
            }
            composable("order/{id}") {
                OrderDetailScreen(appViewModel { c, s -> OrderDetailViewModel(c.orders, s) }) { nav.navigate("payment/$it") }
            }
            composable("profile") {
                ProfileScreen(session?.email.orEmpty(), c.baseUrl()) { scope.launch { c.auth.logout() } }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * Выход с экрана успеха на вкладку. В отличие от switchTab состояние текущей вкладки не сохраняется:
 * иначе вкладка «Корзина» потом открылась бы на старом экране «Заказ оплачен»
 */
private fun NavHostController.leaveCheckout(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = false }
    launchSingleTop = true
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?, cartCount: Int) {
    NavigationBar {
        TABS.forEach { tab ->
            // NavigationBarItem убирает иконку из дерева доступности, когда есть подпись, — вместе с бейджем.
            // Количество товаров дублируется в описании пункта, иначе TalkBack его не прочитает
            val description = if (tab.route == "cart" && cartCount > 0) {
                stringResource(R.string.cart_with_items, cartCount)
            } else null
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { nav.switchTab(tab.route) },
                icon = {
                    if (tab.route == "cart" && cartCount > 0) {
                        BadgedBox(badge = { Badge(Modifier.testTag("cart_badge")) { Text("$cartCount") } }) {
                            Icon(tab.icon, contentDescription = null)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.testTag("nav_${tab.route}").semantics {
                    if (description != null) contentDescription = description
                },
            )
        }
    }
}

/** Маркер «сессия ещё не прочитана» — отличается от null «не вошёл» */
private val LOADING = kz.duken.mobile.data.local.Session("\u0000", "\u0000")
