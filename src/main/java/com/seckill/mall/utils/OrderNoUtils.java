package com.seckill.mall.utils;

import com.seckill.mall.common.Constants;

import java.util.concurrent.atomic.AtomicLong;

public class OrderNoUtils {

    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    private static final long MAX_SEQUENCE = 9999;
    private static long LAST_TIME_STAMP = -1L;

    private OrderNoUtils() {}

    public static synchronized String generateOrderNo() {
        long currentTimeMillis = System.currentTimeMillis();

        if (currentTimeMillis == LAST_TIME_STAMP) {
            long seq = SEQUENCE.incrementAndGet();
            if (seq > MAX_SEQUENCE) {
                currentTimeMillis = waitNextMillis(LAST_TIME_STAMP);
                SEQUENCE.set(0);
            }
        } else {
            SEQUENCE.set(0);
        }

        LAST_TIME_STAMP = currentTimeMillis;
        return Constants.ORDER_NO_PREFIX + currentTimeMillis
                + String.format("%04d", SEQUENCE.get());
    }

    private static long waitNextMillis(long lastTimeStamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimeStamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
