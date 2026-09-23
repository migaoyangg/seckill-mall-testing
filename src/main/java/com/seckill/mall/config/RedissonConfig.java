package com.seckill.mall.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${redisson.address}")
    private String address;

    @Value("${redisson.database:0}")
    private int database;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(address).setDatabase(database);
        if (password != null && !password.isEmpty()) {
            config.useSingleServer().setPassword(password);
        }
        return Redisson.create(config);
    }
}
