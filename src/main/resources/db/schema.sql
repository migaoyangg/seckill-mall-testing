-- ============================================================
-- 高并发秒杀商城系统 - 数据库建表脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS seckill_mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE seckill_mall;

-- 用户表
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(32)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname`    VARCHAR(32)  DEFAULT '' COMMENT '昵称',
    `phone`       VARCHAR(11)  DEFAULT NULL COMMENT '手机号',
    `email`       VARCHAR(64)  DEFAULT NULL COMMENT '邮箱',
    `avatar`      VARCHAR(256) DEFAULT NULL COMMENT '头像URL',
    `role`        TINYINT      NOT NULL DEFAULT 0 COMMENT '角色: 0-普通用户, 1-管理员',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`   VARCHAR(45) DEFAULT NULL COMMENT '最后登录IP',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- 商品分类表
CREATE TABLE `category` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name`        VARCHAR(32) NOT NULL COMMENT '分类名称',
    `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类ID',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '排序值',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品分类表';

-- 商品表
CREATE TABLE `product` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `name`        VARCHAR(128)  NOT NULL COMMENT '商品名称',
    `description` VARCHAR(512)  DEFAULT '' COMMENT '商品描述',
    `price`       DECIMAL(10,2) NOT NULL COMMENT '商品价格',
    `stock`       INT           NOT NULL DEFAULT 0 COMMENT '库存',
    `category_id` BIGINT        NOT NULL COMMENT '分类ID',
    `brand`       VARCHAR(64)   DEFAULT '' COMMENT '品牌',
    `main_image`  VARCHAR(256)  DEFAULT '' COMMENT '主图URL',
    `detail_images` TEXT        DEFAULT NULL COMMENT '详情图JSON',
    `sales`       INT           NOT NULL DEFAULT 0 COMMENT '销量',
    `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态: 0-下架, 1-上架',
    `version`     INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_status_sales` (`status`, `sales` DESC),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品表';

-- 秒杀商品表
CREATE TABLE `seckill_goods` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '秒杀商品ID',
    `product_id`    BIGINT        NOT NULL COMMENT '关联商品ID',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    `stock_count`   INT           NOT NULL COMMENT '秒杀库存',
    `original_price` DECIMAL(10,2) NOT NULL COMMENT '原价',
    `start_time`    DATETIME      NOT NULL COMMENT '开始时间',
    `end_time`      DATETIME      NOT NULL COMMENT '结束时间',
    `limit_per_user` INT          NOT NULL DEFAULT 1 COMMENT '每人限购',
    `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '状态: 0-未开始, 1-进行中, 2-已结束',
    `version`       INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_id` (`product_id`),
    KEY `idx_start_end_time` (`start_time`, `end_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='秒杀商品表';

-- 订单表
CREATE TABLE `orders` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no`      VARCHAR(32)   NOT NULL COMMENT '订单号',
    `user_id`       BIGINT        NOT NULL COMMENT '用户ID',
    `total_amount`  DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    `pay_amount`    DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',
    `pay_type`      TINYINT       DEFAULT NULL COMMENT '支付方式',
    `pay_time`      DATETIME      DEFAULT NULL COMMENT '支付时间',
    `transaction_no` VARCHAR(64)  DEFAULT NULL COMMENT '支付流水号',
    `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '订单状态: 0-待支付, 1-已支付, 2-已发货, 3-已完成, 4-已取消, 5-超时关闭, 6-退款中, 7-已退款',
    `is_seckill`    TINYINT       NOT NULL DEFAULT 0 COMMENT '是否秒杀订单',
    `seckill_goods_id` BIGINT     DEFAULT NULL COMMENT '秒杀商品ID',
    `refund_time`   DATETIME      DEFAULT NULL COMMENT '退款时间',
    `remark`        VARCHAR(256)  DEFAULT '' COMMENT '备注',
    `version`       INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `deleted`       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_transaction_no` (`transaction_no`),
    UNIQUE KEY `uk_user_seckill_goods` (`user_id`, `seckill_goods_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_status_create_time` (`status`, `create_time`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_seckill_goods_id` (`seckill_goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单表';

-- 订单明细表
CREATE TABLE `order_detail` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    `order_id`      BIGINT        NOT NULL COMMENT '订单ID',
    `product_id`    BIGINT        NOT NULL COMMENT '商品ID',
    `product_name`  VARCHAR(128)  NOT NULL COMMENT '商品名称(快照)',
    `product_image` VARCHAR(256)  DEFAULT '' COMMENT '商品图片(快照)',
    `product_price` DECIMAL(10,2) NOT NULL COMMENT '商品单价(快照)',
    `quantity`      INT           NOT NULL COMMENT '购买数量',
    `subtotal`      DECIMAL(10,2) NOT NULL COMMENT '小计金额',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单明细表';

-- ============================================================
-- 初始数据
-- ============================================================

-- 用户账号不写入仓库。开发或CI环境启动后通过注册接口创建，密码由环境变量注入。

-- 商品分类
INSERT INTO `category` (`name`, `parent_id`, `sort_order`) VALUES
('数码电子', 0, 1),
('手机通讯', 1, 1),
('电脑办公', 1, 2),
('家用电器', 0, 2),
('食品饮料', 0, 3);

-- 示例商品
INSERT INTO `product` (`name`, `description`, `price`, `stock`, `category_id`, `brand`, `main_image`, `sales`) VALUES
('iPhone 15 Pro Max', '苹果最新旗舰手机, A17 Pro芯片', 9999.00, 500, 2, 'Apple', '/images/iphone15.jpg', 1200),
('MacBook Pro 14寸', 'M3 Pro芯片, 18GB内存', 16999.00, 200, 3, 'Apple', '/images/macbook.jpg', 800),
('小米14 Ultra', '骁龙8 Gen3, 徕卡影像', 5999.00, 1000, 2, '小米', '/images/xiaomi14.jpg', 2000),
('Sony WH-1000XM5', '降噪头戴耳机', 2699.00, 300, 2, 'Sony', '/images/sonyxm5.jpg', 500),
('AirPods Pro 2', '主动降噪无线耳机, 支持空间音频', 1899.00, 360, 2, 'Apple', '/images/airpods-pro-2.jpg', 760),
('华为 MateBook X Pro', '轻薄高性能办公本, 3.1K原色触控屏', 10999.00, 120, 3, '华为', '/images/matebook-x-pro.jpg', 340),
('戴森 V12 Detect Slim', '激光显尘无线吸尘器, 多场景清洁', 3990.00, 90, 4, 'Dyson', '/images/dyson-v12.jpg', 180),
('雀巢胶囊咖啡礼盒', '多风味精品咖啡胶囊组合装', 169.00, 800, 5, 'Nespresso', '/images/coffee-box.jpg', 1250),
('小米智能空气净化器 4', '高效除醛除霾, 适合客厅卧室', 899.00, 240, 4, '小米', '/images/mi-air-purifier.jpg', 520);

-- 示例秒杀商品
INSERT INTO `seckill_goods` (`product_id`, `seckill_price`, `stock_count`, `original_price`, `start_time`, `end_time`, `limit_per_user`, `status`) VALUES
(1, 7999.00, 50, 9999.00, '2026-06-20 10:00:00', '2026-06-20 12:00:00', 1, 0),
(3, 3999.00, 100, 5999.00, '2026-06-20 14:00:00', '2026-06-20 16:00:00', 1, 0);
