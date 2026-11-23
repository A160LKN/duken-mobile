package kz.duken.mobile.ui.cart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.duken.mobile.data.api.ApiResult
import kz.duken.mobile.data.api.AppError
import kz.duken.mobile.data.api.Quote
import kz.duken.mobile.data.local.CartItem
import kz.duken.mobile.data.repo.CartRepository
import kz.duken.mobile.data.repo.OrderRepository

data class QuoteState(
    val quote: Quote? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
    val promoError: String? = null,
    val checkingOut: Boolean = false,
    val checkoutError: AppError? = null,
)

class CartViewModel(
    private val cart: CartRepository,
    private val orders: OrderRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    /** Применённый промокод. В SavedStateHandle — переживает поворот экрана и гибель процесса */
    val appliedPromo: StateFlow<String?> = saved.getStateFlow(KEY_PROMO, null)

    fun applyPromo(code: String?) {
        clearPromoError()
        saved[KEY_PROMO] = code?.trim()?.uppercase()?.ifBlank { null }
    }

    val items: StateFlow<List<CartItem>?> = cart.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _quote = MutableStateFlow(QuoteState())
    val quote: StateFlow<QuoteState> = _quote.asStateFlow()

    private var quoteJob: Job? = null

    /** Сумму считает сервер: скидки и цены на устройстве не вычисляются */
    fun requestQuote(items: List<CartItem>, promo: String?) {
        quoteJob?.cancel()
        if (items.isEmpty()) {
            _quote.value = QuoteState(); return
        }
        // promoError не сбрасывается: после отказа корзина пересчитывается без промокода,
        // и этот пересчёт не должен стирать объяснение, почему скидки нет
        _quote.update { it.copy(loading = true, error = null) }
        quoteJob = viewModelScope.launch {
            when (val r = orders.quote(items, promo)) {
                is ApiResult.Ok -> _quote.update { it.copy(quote = r.value, loading = false) }
                is ApiResult.Fail -> {
                    val e = r.error
                    if (e is AppError.Http && e.code == "promo_invalid") {
                        _quote.update { it.copy(loading = false, promoError = e.message) }
                        saved[KEY_PROMO] = null
                    } else {
                        _quote.update { it.copy(loading = false, error = e) }
                    }
                }
            }
        }
    }

    /** Пользователь меняет или заново применяет промокод — старая ошибка больше не относится к делу */
    fun clearPromoError() = _quote.update { it.copy(promoError = null) }

    fun setQuantity(item: CartItem, quantity: Int) = viewModelScope.launch { cart.setQuantity(item, quantity) }
    fun remove(item: CartItem) = viewModelScope.launch { cart.remove(item.productId) }

    fun checkout(onCreated: (orderId: Int) -> Unit) {
        val promo = appliedPromo.value
        val list = items.value.orEmpty()
        if (list.isEmpty() || _quote.value.checkingOut) return
        _quote.update { it.copy(checkingOut = true, checkoutError = null) }
        viewModelScope.launch {
            when (val r = orders.create(list, promo)) {
                is ApiResult.Ok -> {
                    cart.clear()
                    saved[KEY_PROMO] = null
                    _quote.update { QuoteState() }
                    onCreated(r.value.id)
                }
                is ApiResult.Fail -> _quote.update { it.copy(checkingOut = false, checkoutError = r.error) }
            }
        }
    }

    private companion object {
        const val KEY_PROMO = "applied_promo"
    }
}
