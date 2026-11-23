package kz.duken.mobile.ui.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.duken.mobile.data.api.ApiResult
import kz.duken.mobile.data.api.AppError
import kz.duken.mobile.data.api.Category
import kz.duken.mobile.data.api.Product
import kz.duken.mobile.data.repo.CartRepository
import kz.duken.mobile.data.repo.CatalogRepository

data class CatalogState(
    val items: List<Product> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val moreError: AppError? = null,
    /** Ошибка обновления свайпом: показывается сообщением, список остаётся */
    val refreshError: AppError? = null,
    val categories: List<Category> = emptyList(),
    /** Растёт, когда список заменён результатом с другими фильтрами: экран прокручивает его наверх */
    val generation: Int = 0,
) {
    val canLoadMore get() = items.size < total && !loadingMore && moreError == null
}

@OptIn(FlowPreview::class)
class CatalogViewModel(
    private val catalog: CatalogRepository,
    private val cart: CartRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    // Фильтры — в SavedStateHandle: поворот и возврат из фона не сбрасывают поиск
    val query: StateFlow<String> = saved.getStateFlow(KEY_QUERY, "")
    val category: StateFlow<String?> = saved.getStateFlow(KEY_CATEGORY, null)
    val sort: StateFlow<String> = saved.getStateFlow(KEY_SORT, "default")

    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        reload()
        loadCategories()
        viewModelScope.launch {
            // Поиск уходит на сервер, когда пользователь перестал печатать
            combine(query.debounce(400), category, sort) { q, c, s -> Triple(q, c, s) }
                .distinctUntilChanged()
                .drop(1)
                .collect { reload() }
        }
    }

    fun onQuery(value: String) { saved[KEY_QUERY] = value }
    fun onCategory(value: String?) { saved[KEY_CATEGORY] = value }
    fun onSort(value: String) { saved[KEY_SORT] = value }

    fun reload(refresh: Boolean = false) {
        loadJob?.cancel()
        _state.update {
            if (refresh) it.copy(refreshing = true, error = null, moreError = null)
            else it.copy(loading = true, error = null, moreError = null)
        }
        loadJob = viewModelScope.launch {
            when (val r = catalog.page(query.value, category.value, sort.value, offset = 0)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(
                        items = r.value.items, total = r.value.total, loading = false, refreshing = false,
                        generation = if (refresh) it.generation else it.generation + 1,
                    )
                }
                is ApiResult.Fail -> _state.update {
                    if (refresh) {
                        // Обновление свайпом не удалось: список тот же, что был, — оставляем его и сообщаем
                        it.copy(refreshing = false, refreshError = r.error)
                    } else {
                        // Фильтр сменился, а новых данных нет: старый список под новым фильтром — неправда
                        it.copy(items = emptyList(), total = 0, loading = false, error = r.error)
                    }
                }
            }
            if (_state.value.categories.isEmpty()) loadCategories()
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.canLoadMore || s.loading) return
        _state.update { it.copy(loadingMore = true, moreError = null) }
        loadJob = viewModelScope.launch {
            when (val r = catalog.page(query.value, category.value, sort.value, offset = s.items.size)) {
                is ApiResult.Ok -> _state.update { st ->
                    // Товар мог сдвинуться между страницами — без дублей по id
                    val merged = (st.items + r.value.items).distinctBy { it.id }
                    st.copy(items = merged, total = r.value.total, loadingMore = false)
                }
                is ApiResult.Fail -> _state.update { it.copy(loadingMore = false, moreError = r.error) }
            }
        }
    }

    fun refreshErrorShown() = _state.update { it.copy(refreshError = null) }

    fun retryMore() {
        _state.update { it.copy(moreError = null) }
        loadMore()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val r = catalog.categories()
            if (r is ApiResult.Ok) _state.update { it.copy(categories = r.value) }
        }
    }

    /** @return false — больше нет на складе */
    suspend fun addToCart(product: Product): Boolean = cart.add(product)

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_CATEGORY = "category"
        const val KEY_SORT = "sort"
    }
}
