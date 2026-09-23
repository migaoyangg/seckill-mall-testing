package com.seckill.mall.mq;

import com.seckill.mall.dto.SeckillOrderMessage;
import com.seckill.mall.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seckill.async.mode", havingValue = "redis", matchIfMissing = true)
public class RedisSeckillOrderProducer implements SeckillOrderProducer {

    private final RedisUtils redisUtils;

    @Override
    public void send(SeckillOrderMessage message) {
        redisUtils.enqueueSeckillOrder(message);
    }
}
