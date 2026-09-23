package com.seckill.mall.mq;

import com.seckill.mall.common.Constants;
import com.seckill.mall.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seckill.async.mode", havingValue = "rabbitmq")
public class RabbitSeckillOrderProducer implements SeckillOrderProducer {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void send(SeckillOrderMessage message) {
        rabbitTemplate.convertAndSend(
                Constants.MQ_SECKILL_EXCHANGE,
                Constants.MQ_SECKILL_ROUTING_KEY,
                message);
        log.debug("秒杀订单发送到 RabbitMQ: requestId={}, orderNo={}",
                message.getRequestId(), message.getOrderNo());
    }
}
