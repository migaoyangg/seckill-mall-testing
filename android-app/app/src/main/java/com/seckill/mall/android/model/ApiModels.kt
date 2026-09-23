package com.seckill.mall.android.model

data class ApiResult<T>(val code: Int, val message: String?, val data: T?)
data class PageResult<T>(val total: Long?, val records: List<T>?, val page: Int?, val size: Int?)
data class LoginRequest(val username: String, val password: String)
data class LoginData(val token: String, val user: User?)
data class User(val id: Long?, val username: String?, val nickname: String?, val role: Int?)
data class Product(
    val id: Long?, val name: String?, val description: String?, val price: String?,
    val stock: Int?, val brand: String?, val mainImage: String?
)
data class Order(
    val orderNo: String?, val productName: String?, val productPrice: String?,
    val quantity: Int?, val status: Int?, val statusDesc: String?, val totalAmount: String?
)
