package com.seckill.mall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full application context test. Requires the configured MySQL and Redis services.
 * The IT suffix keeps it out of the default unit-test phase.
 */
@SpringBootTest
class SeckillMallApplicationIT {

    @Test
    void contextLoads() {
    }
}
