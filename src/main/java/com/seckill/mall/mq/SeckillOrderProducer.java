package com.seckill.mall.mq;

import com.seckill.mall.dto.SeckillOrderMessage;

public interface SeckillOrderProducer {

    void send(SeckillOrderMessage message);
}
