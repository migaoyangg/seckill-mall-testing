package com.seckill.mall.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockUtils {

    private final RedissonClient redissonClient;

    public RLock lock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        log.debug("获取分布式锁: key={}", lockKey);
        return lock;
    }

    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean result = lock.tryLock(waitTime, leaseTime, unit);
            if (result) {
                log.debug("获取分布式锁成功: key={}", lockKey);
            }
            return result;
        } catch (InterruptedException e) {
            log.error("获取分布式锁异常: key={}", lockKey, e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("释放分布式锁: key={}", lockKey);
        }
    }

    public void unlock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
