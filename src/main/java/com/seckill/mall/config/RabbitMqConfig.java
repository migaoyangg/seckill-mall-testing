package com.seckill.mall.config;

import com.seckill.mall.common.Constants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "seckill.async.mode", havingValue = "rabbitmq")
public class RabbitMqConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(4);
        factory.setMaxConcurrentConsumers(16);
        return factory;
    }

    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(Constants.MQ_SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillOrderQueue() {
        return new Queue(Constants.MQ_SECKILL_QUEUE, true);
    }

    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(Constants.MQ_SECKILL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(Constants.MQ_ORDER_DELAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDelayQueue() {
        return new Queue(Constants.MQ_ORDER_DELAY_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", Constants.MQ_ORDER_DELAY_EXCHANGE,
                "x-dead-letter-routing-key", Constants.MQ_ORDER_TIMEOUT_ROUTING_KEY
        ));
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return new Queue(Constants.MQ_ORDER_TIMEOUT_QUEUE, true);
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange())
                .with(Constants.MQ_ORDER_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderDelayExchange())
                .with(Constants.MQ_ORDER_TIMEOUT_ROUTING_KEY);
    }
}
