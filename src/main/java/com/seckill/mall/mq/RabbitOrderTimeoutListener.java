package com.seckill.mall.mq;

import com.seckill.mall.common.Constants;
import com.seckill.mall.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seckill.async.mode", havingValue = "rabbitmq")
public class RabbitOrderTimeoutListener {

    private final OrderService orderService;

    @RabbitListener(queues = Constants.MQ_ORDER_TIMEOUT_QUEUE)
    public void closeTimeoutOrder(String orderNo) {
        orderService.closeTimeoutOrder(orderNo);
    }
}
