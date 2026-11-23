package kz.duken.mobile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kz.duken.mobile.data.api.Product
import kz.duken.mobile.data.local.CartDao
import kz.duken.mobile.data.local.CartItem
import kz.duken.mobile.data.repo.CartRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCartDao : CartDao {
    val rows = MutableStateFlow<Map<Int, CartItem>>(emptyMap())
    override fun observe(): Flow<List<CartItem>> = MutableStateFlow(rows.value.values.toList())
    override suspend fun find(productId: Int) = rows.value[productId]
    override suspend fun upsert(item: CartItem) { rows.value = rows.value + (item.productId to item) }
    override suspend fun delete(productId: Int) { rows.value = rows.value - productId }
    override suspend fun clear() { rows.value = emptyMap() }
}

class CartRepositoryTest {
    private val dao = FakeCartDao()
    private val repo = CartRepository(dao)
    private val kettle = Product(7, "Чайник", "home", 1_249_000, stock = 3)

    @Test fun addAccumulatesQuantity() = runTest {
        repo.add(kettle); repo.add(kettle, 2)
        assertEquals(3, dao.find(7)!!.quantity)
    }

    @Test fun cannotExceedStock() = runTest {
        assertTrue(repo.add(kettle, 3))
        assertFalse(repo.add(kettle))
        assertEquals(3, dao.find(7)!!.quantity)
    }

    @Test fun partialAddUpToStock() = runTest {
        repo.add(kettle, 2)
        assertTrue(repo.add(kettle, 5))
        assertEquals(3, dao.find(7)!!.quantity)
    }

    @Test fun outOfStockNotAdded() = runTest {
        assertFalse(repo.add(kettle.copy(stock = 0)))
        assertNull(dao.find(7))
    }

    @Test fun serverLimitOf100PerLine() = runTest {
        repo.add(kettle.copy(stock = 500), 150)
        assertEquals(CartRepository.MAX_PER_LINE, dao.find(7)!!.quantity)
    }

    @Test fun zeroQuantityRemovesLine() = runTest {
        repo.add(kettle)
        repo.setQuantity(dao.find(7)!!, 0)
        assertNull(dao.find(7))
    }
}
