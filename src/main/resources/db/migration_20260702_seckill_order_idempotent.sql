-- 秒杀订单幂等与超时扫描索引增量脚本
-- 已初始化过数据库时执行；全新建库可直接使用 schema.sql。

USE seckill_mall;

ALTER TABLE `orders`
    ADD UNIQUE KEY `uk_user_seckill_goods` (`user_id`, `seckill_goods_id`),
    ADD KEY `idx_status_create_time` (`status`, `create_time`);
