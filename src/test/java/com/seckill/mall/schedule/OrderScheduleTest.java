package com.seckill.mall.schedule;

import com.seckill.mall.service.OrderService;
import com.seckill.mall.utils.DistributedLockUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderScheduleTest {

    @Mock
    private OrderService orderService;
    @Mock
    private DistributedLockUtils distributedLockUtils;

    private OrderSchedule schedule;

    @BeforeEach
    void setUp() {
        schedule = new OrderSchedule(orderService, distributedLockUtils);
    }

    @Test
    void skipsTimeoutScanWhenAnotherInstanceHoldsLock() {
        when(distributedLockUtils.tryLock("schedule:order:timeout", 0, 50, TimeUnit.SECONDS))
                .thenReturn(false);

        schedule.closeTimeoutOrders();

        verify(orderService, never()).closeTimeoutOrders();
        verify(distributedLockUtils, never()).unlock("schedule:order:timeout");
    }

    @Test
    void executesTimeoutScanAndReleasesLock() {
        when(distributedLockUtils.tryLock("schedule:order:timeout", 0, 50, TimeUnit.SECONDS))
                .thenReturn(true);

        schedule.closeTimeoutOrders();

        verify(orderService).closeTimeoutOrders();
        verify(distributedLockUtils).unlock("schedule:order:timeout");
    }

    @Test
    void releasesLockWhenTimeoutScanFails() {
        when(distributedLockUtils.tryLock("schedule:order:timeout", 0, 50, TimeUnit.SECONDS))
                .thenReturn(true);
        doThrow(new RuntimeException("database unavailable"))
                .when(orderService).closeTimeoutOrders();

        schedule.closeTimeoutOrders();

        verify(distributedLockUtils).unlock("schedule:order:timeout");
    }
}
