package kz.duken.mobile.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Строка корзины. Корзина хранится на устройстве и не теряется при закрытии приложения. */
@Entity(tableName = "cart_items")
data class CartItem(
    @PrimaryKey val productId: Int,
    val name: String,
    val price: Long,
    val quantity: Int,
    val stock: Int,
)

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items ORDER BY name")
    fun observe(): Flow<List<CartItem>>

    @Query("SELECT * FROM cart_items WHERE productId = :productId")
    suspend fun find(productId: Int): CartItem?

    @Upsert
    suspend fun upsert(item: CartItem)

    @Query("DELETE FROM cart_items WHERE productId = :productId")
    suspend fun delete(productId: Int)

    @Query("DELETE FROM cart_items")
    suspend fun clear()
}

@Database(entities = [CartItem::class], version = 1, exportSchema = true)
abstract class CartDatabase : RoomDatabase() {
    abstract fun cart(): CartDao

    companion object {
        fun create(context: Context): CartDatabase =
            Room.databaseBuilder(context, CartDatabase::class.java, "cart.db").build()
    }
}
