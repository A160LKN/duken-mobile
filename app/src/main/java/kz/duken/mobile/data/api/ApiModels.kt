package kz.duken.mobile.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Модели повторяют контракт shop-api. Суммы — в тиынах: 1 ₸ = 100. */

@Serializable
data class Credentials(val email: String, val password: String)

@Serializable
data class TokenOut(@SerialName("access_token") val accessToken: String)

@Serializable
data class UserOut(val id: Int, val email: String, val role: String)

@Serializable
data class Product(val id: Int, val name: String, val category: String, val price: Long, val stock: Int)

@Serializable
data class ProductPage(val items: List<Product>, val total: Int, val limit: Int, val offset: Int)

@Serializable
data class Category(val name: String, val count: Int)

@Serializable
data class OrderLineIn(@SerialName("product_id") val productId: Int, val quantity: Int)

@Serializable
data class OrderIn(val items: List<OrderLineIn>, @SerialName("promo_code") val promoCode: String? = null)

@Serializable
data class Quote(val subtotal: Long, val discount: Long, val total: Long, @SerialName("promo_code") val promoCode: String?)

@Serializable
data class OrderLine(@SerialName("product_id") val productId: Int, val name: String, val quantity: Int, val price: Long)

@Serializable
data class Order(
    val id: Int,
    val status: String,
    val items: List<OrderLine>,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    @SerialName("promo_code") val promoCode: String?,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PaymentIn(@SerialName("card_token") val cardToken: String)

/** Ошибка бизнес-логики shop-api: {"error": {"code": "...", "message": "..."}} */
@Serializable
data class ErrorBody(val error: ErrorDetail? = null)

@Serializable
data class ErrorDetail(val code: String, val message: String)
