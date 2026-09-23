-- 用户维度秒杀限流 Lua 脚本
-- KEYS[1]: 限流 key seckill:rate:{goodsId}:{userId}
-- ARGV[1]: 窗口秒数
-- ARGV[2]: 窗口内最大请求数

local current = redis.call('incr', KEYS[1])

if current == 1 or redis.call('ttl', KEYS[1]) == -1 then
    redis.call('expire', KEYS[1], tonumber(ARGV[1]))
end

if current > tonumber(ARGV[2]) then
    return 0
end

return 1
