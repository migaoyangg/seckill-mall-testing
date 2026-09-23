package com.seckill.mall.common;

public final class Constants {

    private Constants() {}

    // 用户角色
    public static final int ROLE_USER = 0;
    public static final int ROLE_ADMIN = 1;

    // 用户状态
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_NORMAL = 1;

    // 商品状态
    public static final int PRODUCT_OFF_SHELF = 0;
    public static final int PRODUCT_ON_SHELF = 1;

    // 秒杀状态
    public static final int SECKILL_NOT_START = 0;
    public static final int SECKILL_IN_PROGRESS = 1;
    public static final int SECKILL_ENDED = 2;

    // 订单状态
    public static final int ORDER_UNPAID = 0;
    public static final int ORDER_PAID = 1;
    public static final int ORDER_SHIPPED = 2;
    public static final int ORDER_COMPLETED = 3;
    public static final int ORDER_CANCELLED = 4;
    public static final int ORDER_TIMEOUT = 5;
    public static final int ORDER_REFUNDING = 6;
    public static final int ORDER_REFUNDED = 7;

    // Redis Key 前缀
    public static final String REDIS_TOKEN_PREFIX = "user:token:";
    public static final String REDIS_USER_INFO_PREFIX = "user:info:";
    public static final String REDIS_PRODUCT_DETAIL_PREFIX = "product:detail:";
    public static final String REDIS_PRODUCT_LIST_PREFIX = "product:list:";
    public static final String REDIS_SECKILL_GOODS_PREFIX = "seckill:goods:";
    public static final String REDIS_SECKILL_STOCK_PREFIX = "seckill:stock:";
    public static final String REDIS_SECKILL_LIMIT_PREFIX = "seckill:limit:";
    public static final String REDIS_SECKILL_REPEAT_PREFIX = "seckill:repeat:";
    public static final String REDIS_SECKILL_RATE_PREFIX = "seckill:rate:";
    public static final String REDIS_SECKILL_RESULT_PREFIX = "seckill:result:";
    public static final String REDIS_SECKILL_ORDER_QUEUE = "seckill:order:queue";
    public static final String REDIS_ORDER_PAY_CALLBACK_PREFIX = "order:pay:callback:";
    public static final String REDIS_ORDER_PREFIX = "order:info:";

    // RabbitMQ
    public static final String MQ_SECKILL_EXCHANGE = "seckill.order.exchange";
    public static final String MQ_SECKILL_QUEUE = "seckill.order.queue";
    public static final String MQ_SECKILL_ROUTING_KEY = "seckill.order.create";
    public static final String MQ_ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String MQ_ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String MQ_ORDER_TIMEOUT_QUEUE = "order.timeout.queue";
    public static final String MQ_ORDER_DELAY_ROUTING_KEY = "order.delay.close";
    public static final String MQ_ORDER_TIMEOUT_ROUTING_KEY = "order.timeout.close";

    // 订单号前缀
    public static final String ORDER_NO_PREFIX = "SK";
}
