package com.seckill.mall.schedule;

import com.seckill.mall.service.SeckillStockService;
import com.seckill.mall.utils.DistributedLockUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillSchedule {

    private final SeckillStockService seckillStockService;
    private final DistributedLockUtils distributedLockUtils;

    @Scheduled(fixedRate = 60000)
    public void preheatSeckillStock() {
        String lockKey = "schedule:seckill:preheat";
        boolean locked = distributedLockUtils.tryLock(lockKey, 0, 50, TimeUnit.SECONDS);
        if (!locked) {
            log.debug("库存预热任务: 其他实例正在执行, 跳过");
            return;
        }

        try {
            seckillStockService.preheatAllActiveStock();
        } catch (Exception e) {
            log.error("库存预热定时任务异常", e);
        } finally {
            distributedLockUtils.unlock(lockKey);
        }
    }
}
