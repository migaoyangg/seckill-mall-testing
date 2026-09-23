package com.seckill.mall.mq;

import com.seckill.mall.common.Constants;
import com.seckill.mall.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seckill.async.mode", havingValue = "rabbitmq")
public class RabbitSeckillOrderListener {

    private final SeckillOrderMessageHandler seckillOrderMessageHandler;

    @RabbitListener(queues = Constants.MQ_SECKILL_QUEUE)
    public void consume(SeckillOrderMessage message) {
        seckillOrderMessageHandler.handle(message);
    }
}
