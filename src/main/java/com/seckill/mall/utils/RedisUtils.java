package com.seckill.mall.utils;

import com.seckill.mall.common.Constants;
import com.seckill.mall.dto.SeckillOrderMessage;
import com.seckill.mall.vo.SeckillResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final StringRedisSerializer STRING_SERIALIZER = new StringRedisSerializer();

    private DefaultRedisScript<Long> seckillDeductScript;
    private DefaultRedisScript<Long> seckillRollbackScript;
    private DefaultRedisScript<Long> seckillRateLimitScript;

    private DefaultRedisScript<Long> getSeckillDeductScript() {
        if (seckillDeductScript == null) {
            seckillDeductScript = new DefaultRedisScript<>();
            seckillDeductScript.setScriptSource(
                    new ResourceScriptSource(new ClassPathResource("lua/seckill_deduct.lua")));
            seckillDeductScript.setResultType(Long.class);
        }
        return seckillDeductScript;
    }

    private DefaultRedisScript<Long> getSeckillRollbackScript() {
        if (seckillRollbackScript == null) {
            seckillRollbackScript = new DefaultRedisScript<>();
            seckillRollbackScript.setScriptSource(
                    new ResourceScriptSource(new ClassPathResource("lua/seckill_rollback.lua")));
            seckillRollbackScript.setResultType(Long.class);
        }
        return seckillRollbackScript;
    }

    private DefaultRedisScript<Long> getSeckillRateLimitScript() {
        if (seckillRateLimitScript == null) {
            seckillRateLimitScript = new DefaultRedisScript<>();
            seckillRateLimitScript.setScriptSource(
                    new ResourceScriptSource(new ClassPathResource("lua/seckill_rate_limit.lua")));
            seckillRateLimitScript.setResultType(Long.class);
        }
        return seckillRateLimitScript;
    }

    // ==================== 基础操作 ====================

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public String get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    public <T> T getObject(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null && clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return null;
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    // ==================== 原子操作 ====================

    public Long decrement(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    // ==================== Hash 操作 ====================

    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    // ==================== SetIfAbsent ====================

    public Boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    // ==================== 秒杀 Lua 脚本 ====================

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Long executeLongScript(DefaultRedisScript<Long> script, List<String> keys, String... args) {
        return redisTemplate.execute(
                script,
                STRING_SERIALIZER,
                (RedisSerializer) STRING_SERIALIZER,
                keys,
                (Object[]) args);
    }

    public Long executeSeckillDeduct(Long seckillGoodsId, Long userId, Integer limitPerUser) {
        String stockKey = Constants.REDIS_SECKILL_STOCK_PREFIX + seckillGoodsId;
        String limitKey = Constants.REDIS_SECKILL_LIMIT_PREFIX + seckillGoodsId + ":" + userId;
        List<String> keys = List.of(stockKey, limitKey);

        try {
            Long result = executeLongScript(
                    getSeckillDeductScript(),
                    keys,
                    String.valueOf(userId),
                    String.valueOf(limitPerUser));
            log.debug("秒杀扣减: goodsId={}, userId={}, result={}", seckillGoodsId, userId, result);
            return result;
        } catch (Exception e) {
            log.error("秒杀Lua脚本执行异常: goodsId={}, userId={}", seckillGoodsId, userId, e);
            return null;
        }
    }

    public boolean acquireSeckillRateLimit(Long seckillGoodsId, Long userId, int windowSeconds, int maxRequests) {
        String rateKey = Constants.REDIS_SECKILL_RATE_PREFIX + seckillGoodsId + ":" + userId;
        try {
            Long result = executeLongScript(
                    getSeckillRateLimitScript(),
                    Collections.singletonList(rateKey),
                    String.valueOf(windowSeconds),
                    String.valueOf(maxRequests));
            return Long.valueOf(1L).equals(result);
        } catch (Exception e) {
            log.error("秒杀限流脚本执行异常: goodsId={}, userId={}", seckillGoodsId, userId, e);
            return false;
        }
    }

    public void rollbackSeckillDeduct(Long seckillGoodsId, Long userId) {
        String stockKey = Constants.REDIS_SECKILL_STOCK_PREFIX + seckillGoodsId;
        String limitKey = Constants.REDIS_SECKILL_LIMIT_PREFIX + seckillGoodsId + ":" + userId;
        try {
            redisTemplate.execute(getSeckillRollbackScript(), List.of(stockKey, limitKey));
            log.info("秒杀预扣回滚: goodsId={}, userId={}", seckillGoodsId, userId);
        } catch (Exception e) {
            log.error("秒杀预扣回滚异常: goodsId={}, userId={}", seckillGoodsId, userId, e);
        }
    }

    // ==================== 库存预热 ====================

    public void preheatStock(Long seckillGoodsId, Integer stock) {
        String stockKey = Constants.REDIS_SECKILL_STOCK_PREFIX + seckillGoodsId;
        set(stockKey, stock);
        log.info("库存预热: goodsId={}, stock={}", seckillGoodsId, stock);
    }

    public Long getSeckillStock(Long seckillGoodsId) {
        String stockKey = Constants.REDIS_SECKILL_STOCK_PREFIX + seckillGoodsId;
        String value = get(stockKey);
        return value != null ? Long.parseLong(value) : null;
    }

    public void rollbackStock(Long seckillGoodsId) {
        String stockKey = Constants.REDIS_SECKILL_STOCK_PREFIX + seckillGoodsId;
        increment(stockKey);
        log.info("库存回滚: goodsId={}", seckillGoodsId);
    }

    public boolean setSeckillRepeatFlag(Long seckillGoodsId, Long userId) {
        String repeatKey = Constants.REDIS_SECKILL_REPEAT_PREFIX + seckillGoodsId + ":" + userId;
        Boolean result = setIfAbsent(repeatKey, "1", 30, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(result);
    }

    public void deleteSeckillRepeatFlag(Long seckillGoodsId, Long userId) {
        String repeatKey = Constants.REDIS_SECKILL_REPEAT_PREFIX + seckillGoodsId + ":" + userId;
        delete(repeatKey);
    }

    // ==================== 秒杀异步队列与结果 ====================

    public void enqueueSeckillOrder(SeckillOrderMessage message) {
        redisTemplate.opsForList().leftPush(Constants.REDIS_SECKILL_ORDER_QUEUE, message);
        log.debug("秒杀订单入队: requestId={}, orderNo={}", message.getRequestId(), message.getOrderNo());
    }

    public SeckillOrderMessage pollSeckillOrder() {
        Object value = redisTemplate.opsForList().rightPop(Constants.REDIS_SECKILL_ORDER_QUEUE);
        if (value instanceof SeckillOrderMessage message) {
            return message;
        }
        if (value != null) {
            log.warn("秒杀队列消息类型异常: value={}", value);
        }
        return null;
    }

    public void setSeckillResult(String requestId, SeckillResultVO result) {
        String key = Constants.REDIS_SECKILL_RESULT_PREFIX + requestId;
        set(key, result, 30, TimeUnit.MINUTES);
    }

    public SeckillResultVO getSeckillResult(String requestId) {
        String key = Constants.REDIS_SECKILL_RESULT_PREFIX + requestId;
        return getObject(key, SeckillResultVO.class);
    }

    public void bindSeckillRequestUser(String requestId, Long userId) {
        set(Constants.REDIS_SECKILL_RESULT_PREFIX + "user:" + requestId,
                String.valueOf(userId), 30, TimeUnit.MINUTES);
    }

    public Long getSeckillRequestUser(String requestId) {
        String value = get(Constants.REDIS_SECKILL_RESULT_PREFIX + "user:" + requestId);
        return value == null ? null : Long.valueOf(value);
    }
}
