package com.seckill.mall.android.network

import android.content.Context
import com.seckill.mall.android.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object Session {
    private const val TOKEN = "token"
    fun save(context: Context, token: String) = context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().putString(TOKEN, token).apply()
    fun token(context: Context): String? = context.getSharedPreferences("session", Context.MODE_PRIVATE).getString(TOKEN, null)
    fun clear(context: Context) = context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().apply()
}

object ApiClient {
    fun service(context: Context): ApiService {
        val auth = Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                Session.token(context)?.let { header("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(auth).addInterceptor(logging).build()
        return Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
}
