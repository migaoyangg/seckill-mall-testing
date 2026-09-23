package com.seckill.mall.mq;

public interface OrderTimeoutProducer {

    void send(String orderNo, long delayMillis);
}
