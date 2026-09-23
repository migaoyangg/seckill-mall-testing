-- 秒杀预扣回滚 Lua 脚本
-- KEYS[1]: 秒杀库存 key  seckill:stock:{id}
-- KEYS[2]: 限购 key      seckill:limit:{id}:{userId}

redis.call('incr', KEYS[1])

local buyCount = tonumber(redis.call('get', KEYS[2]))
if buyCount ~= nil then
    if buyCount <= 1 then
        redis.call('del', KEYS[2])
    else
        redis.call('decr', KEYS[2])
    end
end

return 1
