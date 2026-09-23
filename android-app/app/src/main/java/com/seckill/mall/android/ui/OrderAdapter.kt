package com.seckill.mall.android.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.seckill.mall.android.databinding.ItemOrderBinding
import com.seckill.mall.android.model.Order

class OrderAdapter : RecyclerView.Adapter<OrderAdapter.Holder>() {
    private val items = mutableListOf<Order>()
    fun submit(list: List<Order>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    class Holder(private val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Order) {
            binding.orderNo.text = "订单号：${item.orderNo ?: "-"}"
            binding.productName.text = "商品：${item.productName ?: "-"} × ${item.quantity ?: 1}"
            binding.orderStatus.text = "状态：${item.statusDesc ?: item.status ?: "未知"}"
        }
    }
}
