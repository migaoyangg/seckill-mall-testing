package com.seckill.mall.mq;

import com.seckill.mall.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seckill.async.mode", havingValue = "rabbitmq")
public class RabbitOrderTimeoutProducer implements OrderTimeoutProducer {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void send(String orderNo, long delayMillis) {
        rabbitTemplate.convertAndSend(
                Constants.MQ_ORDER_DELAY_EXCHANGE,
                Constants.MQ_ORDER_DELAY_ROUTING_KEY,
                orderNo,
                message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                    return message;
                });
        log.debug("订单超时消息已发送: orderNo={}, delayMillis={}", orderNo, delayMillis);
    }
}
