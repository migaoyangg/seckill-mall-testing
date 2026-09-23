package com.seckill.mall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.seckill.mall.mapper")
@EnableScheduling
public class SeckillMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillMallApplication.class, args);
    }
}
