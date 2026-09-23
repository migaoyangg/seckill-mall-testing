package com.seckill.mall.service.impl;

import com.seckill.mall.common.Constants;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.entity.Order;
import com.seckill.mall.entity.OrderDetail;
import com.seckill.mall.entity.Product;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.mapper.OrderDetailMapper;
import com.seckill.mall.mapper.OrderMapper;
import com.seckill.mall.mapper.ProductMapper;
import com.seckill.mall.mapper.SeckillGoodsMapper;
import com.seckill.mall.mq.OrderTimeoutProducer;
import com.seckill.mall.utils.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private SeckillGoodsMapper seckillGoodsMapper;
    @Mock
    private RedisUtils redisUtils;
    @Mock
    private OrderTimeoutProducer orderTimeoutProducer;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderMapper, orderDetailMapper, productMapper,
                seckillGoodsMapper, redisUtils, orderTimeoutProducer);
    }

    @Test
    void createOrderRejectsUnknownProductWithoutDeductingStock() {
        when(productMapper.selectById(10L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createOrder(20L, 10L, 1));

        assertEquals(ResultCode.PRODUCT_NOT_FOUND.getCode(), exception.getCode());
        verify(productMapper, never()).deductStock(anyLong(), any(), any());
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void createOrderPersistsOrderDetailAndSchedulesTimeout() {
        Product product = product(10L, 8, Constants.PRODUCT_ON_SHELF);
        when(productMapper.selectById(10L)).thenReturn(product);
        when(productMapper.deductStock(10L, 2, 3)).thenReturn(1);
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Order.class).setId(99L);
            return 1;
        });

        String orderNo = service.createOrder(20L, 10L, 2);

        assertNotNull(orderNo);
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertEquals(new BigDecimal("39.98"), orderCaptor.getValue().getPayAmount());
        assertEquals(Constants.ORDER_UNPAID, orderCaptor.getValue().getStatus());

        ArgumentCaptor<OrderDetail> detailCaptor = ArgumentCaptor.forClass(OrderDetail.class);
        verify(orderDetailMapper).insert(detailCaptor.capture());
        assertEquals(99L, detailCaptor.getValue().getOrderId());
        assertEquals(2, detailCaptor.getValue().getQuantity());
        assertEquals(new BigDecimal("39.98"), detailCaptor.getValue().getSubtotal());
        verify(redisUtils).set(eq("order:timeout:" + orderNo), any(String.class), eq(20L), any());
        verify(orderTimeoutProducer).send(orderNo, 15 * 60 * 1000L);
    }

    @Test
    void cancelOrderRestoresEveryNormalOrderDetailAndDeletesTimeout() {
        Order order = order(99L, "O-1", Constants.ORDER_UNPAID, 0);
        OrderDetail first = detail(99L, 10L, 2);
        OrderDetail second = detail(99L, 11L, 1);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderDetailMapper.selectList(any())).thenReturn(List.of(first, second));

        service.cancelOrder("O-1", 20L);

        verify(orderMapper).updateById(any(Order.class));
        verify(productMapper, times(2)).update(eq(null), any());
        verify(redisUtils).delete("order:timeout:O-1");
    }

    @Test
    void payCallbackUpdatesPendingOrderAndDeletesTimeout() {
        Order order = order(99L, "O-1", Constants.ORDER_UNPAID, 0);
        when(redisUtils.setIfAbsent(any(), any(), anyLong(), any())).thenReturn(true);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.update(eq(null), any())).thenReturn(1);

        service.payCallback("O-1", 20L, 1, "TX-1");

        verify(orderMapper).update(eq(null), any());
        verify(redisUtils).delete("order:timeout:O-1");
    }

    @Test
    void payCallbackIsIdempotentForSameTransactionOnPaidOrder() {
        Order order = order(99L, "O-1", Constants.ORDER_PAID, 0);
        order.setTransactionNo("TX-1");
        when(redisUtils.setIfAbsent(any(), any(), anyLong(), any())).thenReturn(false);
        when(orderMapper.selectOne(any())).thenReturn(order);

        service.payCallback("O-1", 20L, 1, "TX-1");

        verify(orderMapper, never()).update(eq(null), any());
        verify(redisUtils, never()).delete("order:timeout:O-1");
    }

    @Test
    void closeTimeoutSeckillOrderRestoresDatabaseAndRedisStock() {
        Order order = order(99L, "O-1", Constants.ORDER_UNPAID, 1);
        order.setSeckillGoodsId(8L);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(orderMapper.update(eq(null), any())).thenReturn(1);

        service.closeTimeoutOrder("O-1");

        verify(seckillGoodsMapper).update(eq(null), any());
        verify(redisUtils).rollbackStock(8L);
        verify(redisUtils).delete("order:timeout:O-1");
    }

    @Test
    void closeTimeoutOrderIgnoresAlreadyPaidOrder() {
        Order order = order(99L, "O-1", Constants.ORDER_PAID, 0);
        when(orderMapper.selectOne(any())).thenReturn(order);

        service.closeTimeoutOrder("O-1");

        verify(orderMapper, never()).update(eq(null), any());
        verify(productMapper, never()).update(eq(null), any());
        verify(redisUtils, never()).delete("order:timeout:O-1");
    }

    @Test
    void refundOrderReturnsAlreadyRefundedOrderWithoutRestoringStockAgain() {
        Order order = order(99L, "O-1", Constants.ORDER_REFUNDED, 0);
        when(orderMapper.selectOne(any())).thenReturn(order);

        service.refundOrder("O-1", 20L);

        verify(orderMapper, never()).update(eq(null), any());
        verify(productMapper, never()).update(eq(null), any());
    }

    private static Product product(Long id, Integer stock, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setName("Keyboard");
        product.setPrice(new BigDecimal("19.99"));
        product.setStock(stock);
        product.setVersion(3);
        product.setStatus(status);
        return product;
    }

    private static Order order(Long id, String orderNo, Integer status, Integer isSeckill) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setUserId(20L);
        order.setStatus(status);
        order.setIsSeckill(isSeckill);
        return order;
    }

    private static OrderDetail detail(Long orderId, Long productId, Integer quantity) {
        OrderDetail detail = new OrderDetail();
        detail.setOrderId(orderId);
        detail.setProductId(productId);
        detail.setQuantity(quantity);
        return detail;
    }
}
