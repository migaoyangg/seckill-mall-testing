package com.seckill.mall.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.seckill.mall.android.databinding.ItemProductBinding
import com.seckill.mall.android.model.Product

class ProductAdapter(private val onClick: (Product) -> Unit) : RecyclerView.Adapter<ProductAdapter.Holder>() {
    private val items = mutableListOf<Product>()
    fun submit(list: List<Product>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    inner class Holder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Product) {
            binding.name.text = item.name ?: "未命名商品"
            binding.price.text = "¥${item.price ?: "0.00"}"
            binding.stock.text = "库存：${item.stock ?: 0}"
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
