package kz.duken.mobile.data.repo

import kz.duken.mobile.data.api.ApiResult
import kz.duken.mobile.data.api.Credentials
import kz.duken.mobile.data.api.OrderIn
import kz.duken.mobile.data.api.OrderLineIn
import kz.duken.mobile.data.api.PaymentIn
import kz.duken.mobile.data.api.ShopApi
import kz.duken.mobile.data.api.apiCall
import kz.duken.mobile.data.local.CartDao
import kz.duken.mobile.data.local.CartItem
import kz.duken.mobile.data.local.Session
import kz.duken.mobile.data.local.SessionStore
import kz.duken.mobile.data.api.Product

class AuthRepository(
    private val api: () -> ShopApi,
    private val writeApi: () -> ShopApi,
    private val store: SessionStore,
) {
    val session = store.session

    suspend fun login(email: String, password: String): ApiResult<Unit> {
        val normalized = email.trim().lowercase()
        return when (val r = apiCall { api().login(Credentials(normalized, password)) }) {
            is ApiResult.Ok -> {
                store.save(Session(r.value.accessToken, normalized)); ApiResult.Ok(Unit)
            }
            is ApiResult.Fail -> r
        }
    }

    /** Регистрация и сразу вход — второй экран вводить пароль не нужен. */
    suspend fun register(email: String, password: String): ApiResult<Unit> =
        when (val r = apiCall { writeApi().register(Credentials(email.trim().lowercase(), password)) }) {
            is ApiResult.Ok -> login(email, password)
            is ApiResult.Fail -> r
        }

    suspend fun logout() = store.clear()
}

class CatalogRepository(private val api: () -> ShopApi) {
    suspend fun page(query: String?, category: String?, sort: String, offset: Int, limit: Int = PAGE_SIZE) =
        apiCall { api().products(query?.takeIf { it.isNotBlank() }, category, sort, limit, offset) }

    suspend fun categories() = apiCall { api().categories() }
    suspend fun product(id: Int) = apiCall { api().product(id) }

    companion object {
        const val PAGE_SIZE = 20
    }
}

class CartRepository(private val dao: CartDao) {
    val items = dao.observe()

    /** Добавить товар. Больше, чем есть на складе, положить нельзя. */
    suspend fun add(product: Product, quantity: Int = 1): Boolean {
        val current = dao.find(product.id)?.quantity ?: 0
        val wanted = (current + quantity).coerceAtMost(minOf(product.stock, MAX_PER_LINE))
        if (wanted <= current) return false
        dao.upsert(CartItem(product.id, product.name, product.price, wanted, product.stock))
        return true
    }

    suspend fun setQuantity(item: CartItem, quantity: Int) {
        when {
            quantity <= 0 -> dao.delete(item.productId)
            else -> dao.upsert(item.copy(quantity = quantity.coerceAtMost(minOf(item.stock, MAX_PER_LINE))))
        }
    }

    suspend fun remove(productId: Int) = dao.delete(productId)
    suspend fun clear() = dao.clear()

    companion object {
        /** shop-api принимает не больше 100 штук одного товара в заказе */
        const val MAX_PER_LINE = 100
    }
}

/** Создание, оплата и отмена — через writeApi: эти запросы нельзя тихо повторять */
class OrderRepository(private val api: () -> ShopApi, private val writeApi: () -> ShopApi) {
    private fun body(items: List<CartItem>, promo: String?) =
        OrderIn(items.map { OrderLineIn(it.productId, it.quantity) }, promo?.trim()?.uppercase()?.ifBlank { null })

    suspend fun quote(items: List<CartItem>, promo: String?) = apiCall { api().quote(body(items, promo)) }
    suspend fun create(items: List<CartItem>, promo: String?) = apiCall { writeApi().createOrder(body(items, promo)) }
    suspend fun list() = apiCall { api().orders() }
    suspend fun get(id: Int) = apiCall { api().order(id) }
    suspend fun pay(id: Int, cardToken: String) = apiCall { writeApi().pay(id, PaymentIn(cardToken)) }
    suspend fun cancel(id: Int) = apiCall { writeApi().cancel(id) }
}
