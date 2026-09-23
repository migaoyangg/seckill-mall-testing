package com.seckill.mall.android.network

import com.seckill.mall.android.model.*
import retrofit2.http.*

interface ApiService {
    @POST("user/login")
    suspend fun login(@Body request: LoginRequest): ApiResult<LoginData>

    @GET("product/list")
    suspend fun products(@Query("page") page: Int = 1, @Query("size") size: Int = 20): ApiResult<PageResult<Product>>

    @GET("product/detail/{id}")
    suspend fun product(@Path("id") id: Long): ApiResult<Product>

    @GET("order/list")
    suspend fun orders(@Query("page") page: Int = 1, @Query("size") size: Int = 20): ApiResult<PageResult<Order>>

    @POST("order/create")
    suspend fun createOrder(@Query("productId") productId: Long, @Query("quantity") quantity: Int = 1): ApiResult<String>
}
