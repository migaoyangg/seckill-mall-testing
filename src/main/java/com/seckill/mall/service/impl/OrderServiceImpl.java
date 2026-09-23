package com.seckill.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.mall.common.Constants;
import com.seckill.mall.common.PageResult;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.entity.*;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.mapper.OrderDetailMapper;
import com.seckill.mall.mapper.OrderMapper;
import com.seckill.mall.mapper.ProductMapper;
import com.seckill.mall.mapper.SeckillGoodsMapper;
import com.seckill.mall.mq.OrderTimeoutProducer;
import com.seckill.mall.service.OrderService;
import com.seckill.mall.utils.OrderNoUtils;
import com.seckill.mall.utils.RedisUtils;
import com.seckill.mall.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final ProductMapper productMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisUtils redisUtils;
    private final OrderTimeoutProducer orderTimeoutProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(Long userId, Long productId, Integer quantity) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (product.getStatus() != Constants.PRODUCT_ON_SHELF) {
            throw new BusinessException(ResultCode.PRODUCT_OFF_SHELF);
        }
        if (product.getStock() < quantity) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }

        int affected = productMapper.deductStock(productId, quantity, product.getVersion());
        if (affected == 0) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }

        String orderNo = OrderNoUtils.generateOrderNo();
        BigDecimal totalAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setStatus(Constants.ORDER_UNPAID);
        order.setIsSeckill(0);
        orderMapper.insert(order);

        OrderDetail detail = new OrderDetail();
        detail.setOrderId(order.getId());
        detail.setProductId(productId);
        detail.setProductName(product.getName());
        detail.setProductImage(product.getMainImage());
        detail.setProductPrice(product.getPrice());
        detail.setQuantity(quantity);
        detail.setSubtotal(totalAmount);
        orderDetailMapper.insert(detail);

        long timeout = System.currentTimeMillis() + 15 * 60 * 1000;
        redisUtils.set("order:timeout:" + orderNo, String.valueOf(timeout), 20, TimeUnit.MINUTES);
        orderTimeoutProducer.send(orderNo, 15 * 60 * 1000L);

        log.info("创建普通订单: orderNo={}, userId={}", orderNo, userId);
        return orderNo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSeckillOrder(Long seckillGoodsId, Long userId) {
        return createSeckillOrder(seckillGoodsId, userId, OrderNoUtils.generateOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSeckillOrder(Long seckillGoodsId, Long userId, String orderNo) {
        Order existingOrder = getExistingSeckillOrder(seckillGoodsId, userId, orderNo);
        if (existingOrder != null) {
            log.info("秒杀订单已存在, 幂等返回: orderNo={}, userId={}, goodsId={}",
                    existingOrder.getOrderNo(), userId, seckillGoodsId);
            return existingOrder.getOrderNo();
        }

        SeckillGoods seckillGoods = seckillGoodsMapper.selectById(seckillGoodsId);
        if (seckillGoods == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "秒杀活动不存在");
        }

        Product product = productMapper.selectById(seckillGoods.getProductId());
        String productName = product != null ? product.getName() : "未知商品";
        String productImage = product != null ? product.getMainImage() : "";

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(seckillGoods.getSeckillPrice());
        order.setPayAmount(seckillGoods.getSeckillPrice());
        order.setStatus(Constants.ORDER_UNPAID);
        order.setIsSeckill(1);
        order.setSeckillGoodsId(seckillGoodsId);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            Order duplicateOrder = getExistingSeckillOrder(seckillGoodsId, userId, orderNo);
            if (duplicateOrder != null) {
                log.info("秒杀订单唯一约束命中, 幂等返回: orderNo={}, userId={}, goodsId={}",
                        duplicateOrder.getOrderNo(), userId, seckillGoodsId);
                return duplicateOrder.getOrderNo();
            }
            throw e;
        }

        int affected = orderMapper.deductSeckillStock(seckillGoodsId, seckillGoods.getVersion());
        if (affected == 0) {
            throw new BusinessException(ResultCode.SECKILL_STOCK_EMPTY);
        }

        OrderDetail detail = new OrderDetail();
        detail.setOrderId(order.getId());
        detail.setProductId(seckillGoods.getProductId());
        detail.setProductName(productName);
        detail.setProductImage(productImage);
        detail.setProductPrice(seckillGoods.getSeckillPrice());
        detail.setQuantity(1);
        detail.setSubtotal(seckillGoods.getSeckillPrice());
        orderDetailMapper.insert(detail);

        long timeout = System.currentTimeMillis() + 5 * 60 * 1000;
        redisUtils.set("order:timeout:" + orderNo, String.valueOf(timeout), 10, TimeUnit.MINUTES);
        orderTimeoutProducer.send(orderNo, 5 * 60 * 1000L);

        log.info("创建秒杀订单: orderNo={}, userId={}", orderNo, userId);
        return orderNo;
    }

    private Order getExistingSeckillOrder(Long seckillGoodsId, Long userId, String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId)
                .eq(Order::getSeckillGoodsId, seckillGoodsId)
                .or()
                .eq(Order::getOrderNo, orderNo)
                .last("LIMIT 1");
        return orderMapper.selectOne(wrapper);
    }

    @Override
    public OrderVO getOrderDetail(String orderNo, Long userId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo).eq(Order::getUserId, userId);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return convertToVO(order);
    }

    @Override
    public PageResult<OrderVO> listUserOrders(Long userId, Integer status, Integer page, Integer size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreateTime);

        Page<Order> pageParam = new Page<>(page, size);
        Page<Order> result = orderMapper.selectPage(pageParam, wrapper);

        List<OrderVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), voList, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo, Long userId) {
        Order order = getAndCheckOrder(orderNo, userId);
        if (order.getStatus() != Constants.ORDER_UNPAID) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "当前订单状态不允许取消");
        }

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(Constants.ORDER_CANCELLED);
        orderMapper.updateById(update);

        restoreStock(order);
        redisUtils.delete("order:timeout:" + orderNo);
        log.info("取消订单: orderNo={}", orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(String orderNo, Long userId, Integer payType) {
        payCallback(orderNo, userId, payType, "SIM_PAY_" + orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payCallback(String orderNo, Long userId, Integer payType, String transactionNo) {
        if (transactionNo == null || transactionNo.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "支付流水号不能为空");
        }

        LambdaQueryWrapper<Order> transactionWrapper = new LambdaQueryWrapper<>();
        transactionWrapper.eq(Order::getTransactionNo, transactionNo).last("LIMIT 1");
        Order transactionOrder = orderMapper.selectOne(transactionWrapper);
        if (transactionOrder != null && !transactionOrder.getOrderNo().equals(orderNo)) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "支付流水号已绑定其他订单");
        }

        boolean firstCallback = Boolean.TRUE.equals(redisUtils.setIfAbsent(
                Constants.REDIS_ORDER_PAY_CALLBACK_PREFIX + transactionNo,
                orderNo,
                30,
                TimeUnit.MINUTES));
        if (!firstCallback) {
            log.info("支付回调重复触发: orderNo={}, transactionNo={}", orderNo, transactionNo);
        }

        Order order = getAndCheckOrder(orderNo, userId);
        if (order.getStatus() == Constants.ORDER_PAID) {
            if (transactionNo.equals(order.getTransactionNo())) {
                log.info("支付回调幂等返回: orderNo={}, transactionNo={}", orderNo, transactionNo);
                return;
            }
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单已由其他支付流水完成支付");
        }
        if (order.getStatus() != Constants.ORDER_UNPAID) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "当前订单状态不允许支付");
        }

        UpdateWrapper<Order> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", order.getId())
                .eq("status", Constants.ORDER_UNPAID)
                .set("status", Constants.ORDER_PAID)
                .set("pay_type", payType)
                .set("pay_time", LocalDateTime.now())
                .set("transaction_no", transactionNo);
        int affected;
        try {
            affected = orderMapper.update(null, updateWrapper);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "支付流水号已绑定其他订单");
        }
        if (affected == 0) {
            Order latest = getAndCheckOrder(orderNo, userId);
            if (latest.getStatus() == Constants.ORDER_PAID
                    && transactionNo.equals(latest.getTransactionNo())) {
                log.info("支付并发回调幂等返回: orderNo={}, transactionNo={}", orderNo, transactionNo);
                return;
            }
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单支付状态已变化");
        }

        redisUtils.delete("order:timeout:" + orderNo);
        log.info("订单支付成功: orderNo={}, transactionNo={}", orderNo, transactionNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(String orderNo, Long userId) {
        Order order = getAndCheckOrder(orderNo, userId);
        if (order.getStatus() == Constants.ORDER_REFUNDED) {
            log.info("退款幂等返回: orderNo={}", orderNo);
            return;
        }
        if (order.getStatus() != Constants.ORDER_PAID) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "仅已支付订单允许退款");
        }

        UpdateWrapper<Order> refundingWrapper = new UpdateWrapper<>();
        refundingWrapper.eq("id", order.getId())
                .eq("status", Constants.ORDER_PAID)
                .set("status", Constants.ORDER_REFUNDING);
        int refunding = orderMapper.update(null, refundingWrapper);
        if (refunding == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "订单退款状态已变化");
        }

        UpdateWrapper<Order> refundedWrapper = new UpdateWrapper<>();
        refundedWrapper.eq("id", order.getId())
                .eq("status", Constants.ORDER_REFUNDING)
                .set("status", Constants.ORDER_REFUNDED)
                .set("refund_time", LocalDateTime.now());
        orderMapper.update(null, refundedWrapper);

        restoreStock(order);
        log.info("订单退款完成: orderNo={}", orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeTimeoutOrders() {
        String normalTimeoutTime = LocalDateTime.now().minusMinutes(15)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String seckillTimeoutTime = LocalDateTime.now().minusMinutes(5)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Order> timeoutOrders = orderMapper.selectTimeoutOrders(normalTimeoutTime, seckillTimeoutTime, 100);

        for (Order order : timeoutOrders) {
            try {
                closeTimeoutOrder(order.getOrderNo());
            } catch (Exception e) {
                log.error("关闭超时订单异常: orderNo={}", order.getOrderNo(), e);
            }
        }

        if (!timeoutOrders.isEmpty()) {
            log.info("超时关闭订单完成: 共{}个", timeoutOrders.size());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeTimeoutOrder(String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            log.info("超时关单忽略, 订单不存在: orderNo={}", orderNo);
            return;
        }
        if (order.getStatus() != Constants.ORDER_UNPAID) {
            log.info("超时关单幂等忽略: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        UpdateWrapper<Order> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", order.getId())
                .eq("status", Constants.ORDER_UNPAID)
                .set("status", Constants.ORDER_TIMEOUT);
        int affected = orderMapper.update(null, updateWrapper);
        if (affected > 0) {
            restoreStock(order);
            redisUtils.delete("order:timeout:" + orderNo);
            log.info("超时关闭订单: orderNo={}", orderNo);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);

        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != Constants.ORDER_PAID) {
            throw new BusinessException(ResultCode.ORDER_STATUS_ERROR, "当前订单状态不允许发货");
        }

        Order update = new Order();
        update.setId(order.getId());
        update.setStatus(Constants.ORDER_SHIPPED);
        orderMapper.updateById(update);
        log.info("订单发货: orderNo={}", orderNo);
    }

    private Order getAndCheckOrder(String orderNo, Long userId) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo).eq(Order::getUserId, userId);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void restoreStock(Order order) {
        if (order.getIsSeckill() == 1 && order.getSeckillGoodsId() != null) {
            UpdateWrapper<SeckillGoods> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", order.getSeckillGoodsId())
                    .setSql("stock_count = stock_count + 1");
            seckillGoodsMapper.update(null, updateWrapper);
            redisUtils.rollbackStock(order.getSeckillGoodsId());
        } else {
            LambdaQueryWrapper<OrderDetail> detailWrapper = new LambdaQueryWrapper<>();
            detailWrapper.eq(OrderDetail::getOrderId, order.getId());
            List<OrderDetail> details = orderDetailMapper.selectList(detailWrapper);

            for (OrderDetail detail : details) {
                UpdateWrapper<Product> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", detail.getProductId())
                        .setSql("stock = stock + " + detail.getQuantity());
                productMapper.update(null, updateWrapper);
            }
        }
    }

    private OrderVO convertToVO(Order order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);

        LambdaQueryWrapper<OrderDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDetail::getOrderId, order.getId());
        List<OrderDetail> details = orderDetailMapper.selectList(wrapper);

        vo.setStatusDesc(getStatusDesc(order.getStatus()));

        if (details != null && !details.isEmpty()) {
            OrderDetail firstDetail = details.get(0);
            vo.setProductName(firstDetail.getProductName());
            vo.setProductImage(firstDetail.getProductImage());
            vo.setProductPrice(firstDetail.getProductPrice());
            vo.setQuantity(firstDetail.getQuantity());
        }

        return vo;
    }

    private String getStatusDesc(Integer status) {
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            case 5 -> "超时关闭";
            case 6 -> "退款中";
            case 7 -> "已退款";
            default -> "未知状态";
        };
    }
}
