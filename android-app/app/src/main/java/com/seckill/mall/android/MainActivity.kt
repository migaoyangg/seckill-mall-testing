package com.seckill.mall.android

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.seckill.mall.android.databinding.ActivityMainBinding
import com.seckill.mall.android.databinding.ViewHomeBinding
import com.seckill.mall.android.databinding.ViewLoginBinding
import com.seckill.mall.android.model.Product
import com.seckill.mall.android.network.ApiClient
import com.seckill.mall.android.network.Session
import com.seckill.mall.android.ui.OrderAdapter
import com.seckill.mall.android.ui.ProductAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var main: ActivityMainBinding
    private val api by lazy { ApiClient.service(this) }
    private var home: ViewHomeBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        main = ActivityMainBinding.inflate(layoutInflater)
        setContentView(main.root)
        if (Session.token(this).isNullOrBlank()) showLogin() else showHome()
    }

    private fun showLogin() {
        val binding = ViewLoginBinding.inflate(layoutInflater)
        main.contentContainer.removeAllViews()
        main.contentContainer.addView(binding.root)
        binding.loginButton.setOnClickListener {
            val username = binding.usernameInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            if (username.isBlank() || password.isBlank()) {
                binding.loginMessage.text = "请输入用户名和密码"
                return@setOnClickListener
            }
            binding.loginButton.isEnabled = false
            lifecycleScope.launch {
                runCatching { api.login(com.seckill.mall.android.model.LoginRequest(username, password)) }
                    .onSuccess { result ->
                        if (result.code == 200 && !result.data?.token.isNullOrBlank()) {
                            Session.save(this@MainActivity, result.data!!.token)
                            showHome()
                        } else binding.loginMessage.text = result.message ?: "登录失败"
                    }.onFailure { binding.loginMessage.text = "网络连接失败：${it.message ?: "请检查后端服务"}" }
                binding.loginButton.isEnabled = true
            }
        }
    }

    private fun showHome() {
        val binding = ViewHomeBinding.inflate(layoutInflater)
        home = binding
        main.contentContainer.removeAllViews()
        main.contentContainer.addView(binding.root)
        binding.listView.layoutManager = LinearLayoutManager(this)
        binding.productsButton.setOnClickListener { loadProducts() }
        binding.ordersButton.setOnClickListener { loadOrders() }
        binding.logoutButton.setOnClickListener { Session.clear(this); showLogin() }
        loadProducts()
    }

    private fun loadProducts() {
        val binding = home ?: return
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { api.products() }.onSuccess { result ->
                binding.loading.visibility = View.GONE
                if (result.code == 200) {
                    binding.emptyText.text = if (result.data?.records.isNullOrEmpty()) "暂无商品" else ""
                    binding.listView.adapter = ProductAdapter(::showProduct).also { it.submit(result.data?.records.orEmpty()) }
                } else showError(result.message)
            }.onFailure { binding.loading.visibility = View.GONE; showError("加载商品失败：${it.message}") }
        }
    }

    private fun loadOrders() {
        val binding = home ?: return
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            runCatching { api.orders() }.onSuccess { result ->
                binding.loading.visibility = View.GONE
                if (result.code == 200) {
                    binding.emptyText.text = if (result.data?.records.isNullOrEmpty()) "暂无订单" else ""
                    binding.listView.adapter = OrderAdapter().also { it.submit(result.data?.records.orEmpty()) }
                } else showError(result.message)
            }.onFailure { binding.loading.visibility = View.GONE; showError("加载订单失败：${it.message}") }
        }
    }

    private fun showProduct(product: Product) {
        AlertDialog.Builder(this)
            .setTitle(product.name ?: "商品详情")
            .setMessage("${product.description ?: "暂无描述"}\n\n价格：¥${product.price ?: "0.00"}\n库存：${product.stock ?: 0}")
            .setNegativeButton("关闭", null)
            .setPositiveButton("立即下单") { _, _ -> createOrder(product) }
            .show()
    }

    private fun createOrder(product: Product) {
        val id = product.id ?: return
        lifecycleScope.launch {
            runCatching { api.createOrder(id) }.onSuccess { result ->
                if (result.code == 200) Toast.makeText(this@MainActivity, "下单成功：${result.data}", Toast.LENGTH_LONG).show()
                else showError(result.message)
            }.onFailure { showError("下单失败：${it.message}") }
        }
    }

    private fun showError(message: String?) = Toast.makeText(this, message ?: "请求失败", Toast.LENGTH_SHORT).show()
}
