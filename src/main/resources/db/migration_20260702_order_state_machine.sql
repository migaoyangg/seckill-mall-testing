-- 订单状态机、支付回调幂等与退款字段增量脚本

USE seckill_mall;

ALTER TABLE `orders`
    ADD COLUMN `transaction_no` VARCHAR(64) DEFAULT NULL COMMENT '支付流水号' AFTER `pay_time`,
    ADD COLUMN `refund_time` DATETIME DEFAULT NULL COMMENT '退款时间' AFTER `seckill_goods_id`,
    ADD UNIQUE KEY `uk_transaction_no` (`transaction_no`);
