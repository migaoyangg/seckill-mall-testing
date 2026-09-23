package com.seckill.mall.schedule;

import com.seckill.mall.service.OrderService;
import com.seckill.mall.utils.DistributedLockUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSchedule {

    private final OrderService orderService;
    private final DistributedLockUtils distributedLockUtils;

    @Scheduled(fixedRate = 60000)
    public void closeTimeoutOrders() {
        String lockKey = "schedule:order:timeout";
        boolean locked = distributedLockUtils.tryLock(lockKey, 0, 50, TimeUnit.SECONDS);
        if (!locked) {
            log.debug("关闭超时订单任务: 其他实例正在执行, 跳过");
            return;
        }

        try {
            orderService.closeTimeoutOrders();
        } catch (Exception e) {
            log.error("关闭超时订单任务异常", e);
        } finally {
            distributedLockUtils.unlock(lockKey);
        }
    }
}
