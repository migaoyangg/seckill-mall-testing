-- 压测后一致性校验: 执行前替换 @seckill_goods_id 和 @initial_stock

SET @seckill_goods_id = 1;
SET @initial_stock = 1000;

SELECT COUNT(*) AS success_order_count
FROM orders
WHERE is_seckill = 1
  AND seckill_goods_id = @seckill_goods_id
  AND deleted = 0;

SELECT user_id, COUNT(*) AS order_count
FROM orders
WHERE is_seckill = 1
  AND seckill_goods_id = @seckill_goods_id
  AND deleted = 0
GROUP BY user_id
HAVING COUNT(*) > 1;

SELECT id, stock_count AS mysql_remain_stock
FROM seckill_goods
WHERE id = @seckill_goods_id;

SELECT
    @initial_stock AS initial_stock,
    COUNT(o.id) AS success_order_count,
    sg.stock_count AS mysql_remain_stock,
    @initial_stock - COUNT(o.id) - sg.stock_count AS diff
FROM seckill_goods sg
LEFT JOIN orders o ON o.seckill_goods_id = sg.id
    AND o.is_seckill = 1
    AND o.deleted = 0
WHERE sg.id = @seckill_goods_id
GROUP BY sg.id, sg.stock_count;
