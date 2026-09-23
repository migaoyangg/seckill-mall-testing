-- 演示商品增量数据：适用于已经初始化过 schema.sql 的数据库
USE seckill_mall;

INSERT INTO `product`
(`name`, `description`, `price`, `stock`, `category_id`, `brand`, `main_image`, `sales`, `status`)
SELECT 'AirPods Pro 2', '主动降噪无线耳机, 支持空间音频', 1899.00, 360, 2, 'Apple', '/images/airpods-pro-2.jpg', 760, 1
WHERE NOT EXISTS (SELECT 1 FROM `product` WHERE `name` = 'AirPods Pro 2' AND `deleted` = 0);

INSERT INTO `product`
(`name`, `description`, `price`, `stock`, `category_id`, `brand`, `main_image`, `sales`, `status`)
SELECT '华为 MateBook X Pro', '轻薄高性能办公本, 3.1K原色触控屏', 10999.00, 120, 3, '华为', '/images/matebook-x-pro.jpg', 340, 1
WHERE NOT EXISTS (SELECT 1 FROM `product` WHERE `name` = '华为 MateBook X Pro' AND `deleted` = 0);

INSERT INTO `product`
(`name`, `description`, `price`, `stock`, `category_id`, `brand`, `main_image`, `sales`, `status`)
SELECT '戴森 V12 Detect Slim', '激光显尘无线吸尘器, 多场景清洁', 3990.00, 90, 4, 'Dyson', '/images/dyson-v12.jpg', 180, 1
WHERE NOT EXISTS (SELECT 1 FROM `product` WHERE `name` = '戴森 V12 Detect Slim' AND `deleted` = 0);

INSERT INTO `product`
(`name`, `description`, `price`, `stock`, `category_id`, `brand`, `main_image`, `sales`, `status`)
SELECT '雀巢胶囊咖啡礼盒', '多风味精品咖啡胶囊组合装', 169.00, 800, 5, 'Nespresso', '/images/coffee-box.jpg', 1250, 1
WHERE NOT EXISTS (SELECT 1 FROM `product` WHERE `name` = '雀巢胶囊咖啡礼盒' AND `deleted` = 0);

INSERT INTO `product`
(`name`, `description`, `price`, `stock`, `category_id`, `brand`, `main_image`, `sales`, `status`)
SELECT '小米智能空气净化器 4', '高效除醛除霾, 适合客厅卧室', 899.00, 240, 4, '小米', '/images/mi-air-purifier.jpg', 520, 1
WHERE NOT EXISTS (SELECT 1 FROM `product` WHERE `name` = '小米智能空气净化器 4' AND `deleted` = 0);
