-- 仅扣减库存 (不含限购判断)
-- KEYS[1]: 库存key
-- ARGV[1]: 扣减数量

local stock = tonumber(redis.call('get', KEYS[1]))

if stock == nil or stock < tonumber(ARGV[1]) then
    return -1
end

redis.call('decrby', KEYS[1], ARGV[1])
return stock - tonumber(ARGV[1])
