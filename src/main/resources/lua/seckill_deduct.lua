-- 秒杀库存扣减 Lua 脚本
-- KEYS[1]: 秒杀库存key  seckill:stock:{id}
-- KEYS[2]: 限购key      seckill:limit:{id}:{userId}
-- ARGV[1]: 用户ID
-- ARGV[2]: 限购数量

-- 1. 获取当前库存
local stock = tonumber(redis.call('get', KEYS[1]))

-- 2. 库存不存在或不足
if stock == nil or stock <= 0 then
    return -1
end

-- 3. 判断是否超出限购
local limitKey = KEYS[2]
local buyCount = tonumber(redis.call('get', limitKey))
if buyCount ~= nil and buyCount >= tonumber(ARGV[2]) then
    return -2
end

-- 4. 扣减库存
redis.call('decr', KEYS[1])

-- 5. 记录用户购买次数
if buyCount == nil then
    redis.call('set', limitKey, 1)
    redis.call('expire', limitKey, 7200)
else
    redis.call('incr', limitKey)
end

-- 6. 返回扣减后的库存
return stock - 1
