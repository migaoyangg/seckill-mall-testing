package com.seckill.mall.service;

import com.seckill.mall.common.PageResult;
import com.seckill.mall.vo.OrderVO;

public interface OrderService {

    String createOrder(Long userId, Long productId, Integer quantity);

    String createSeckillOrder(Long seckillGoodsId, Long userId);

    String createSeckillOrder(Long seckillGoodsId, Long userId, String orderNo);

    OrderVO getOrderDetail(String orderNo, Long userId);

    PageResult<OrderVO> listUserOrders(Long userId, Integer status, Integer page, Integer size);

    void cancelOrder(String orderNo, Long userId);

    void payOrder(String orderNo, Long userId, Integer payType);

    void payCallback(String orderNo, Long userId, Integer payType, String transactionNo);

    void refundOrder(String orderNo, Long userId);

    void closeTimeoutOrders();

    void closeTimeoutOrder(String orderNo);

    void shipOrder(String orderNo);
}
