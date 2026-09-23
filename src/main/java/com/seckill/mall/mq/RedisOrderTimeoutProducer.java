package com.seckill.mall.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "seckill.async.mode", havingValue = "redis", matchIfMissing = true)
public class RedisOrderTimeoutProducer implements OrderTimeoutProducer {

    @Override
    public void send(String orderNo, long delayMillis) {
        log.debug("Redis模式使用定时任务扫描超时订单: orderNo={}, delayMillis={}", orderNo, delayMillis);
    }
}
