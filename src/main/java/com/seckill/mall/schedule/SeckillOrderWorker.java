package com.seckill.mall.schedule;

import com.seckill.mall.dto.SeckillOrderMessage;
import com.seckill.mall.mq.SeckillOrderMessageHandler;
import com.seckill.mall.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seckill.async.mode", havingValue = "redis", matchIfMissing = true)
public class SeckillOrderWorker {

    private static final int BATCH_SIZE = 50;

    private final RedisUtils redisUtils;
    private final SeckillOrderMessageHandler seckillOrderMessageHandler;

    @Scheduled(fixedDelay = 50)
    public void consumeSeckillOrders() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            SeckillOrderMessage message = redisUtils.pollSeckillOrder();
            if (message == null) {
                return;
            }
            seckillOrderMessageHandler.handle(message);
        }
    }
}
