package kz.duken.mobile.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ShopApi {
    @POST("auth/register")
    suspend fun register(@Body body: Credentials): UserOut

    @POST("auth/login")
    suspend fun login(@Body body: Credentials): TokenOut

    @GET("auth/me")
    suspend fun me(): UserOut

    @GET("products")
    suspend fun products(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("sort") sort: String = "default",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): ProductPage

    @GET("products/categories")
    suspend fun categories(): List<Category>

    @GET("products/{id}")
    suspend fun product(@Path("id") id: Int): Product

    @POST("orders/quote")
    suspend fun quote(@Body body: OrderIn): Quote

    @POST("orders")
    suspend fun createOrder(@Body body: OrderIn): Order

    @GET("orders")
    suspend fun orders(): List<Order>

    @GET("orders/{id}")
    suspend fun order(@Path("id") id: Int): Order

    @POST("orders/{id}/pay")
    suspend fun pay(@Path("id") id: Int, @Body body: PaymentIn): Order

    @POST("orders/{id}/cancel")
    suspend fun cancel(@Path("id") id: Int): Order
}
