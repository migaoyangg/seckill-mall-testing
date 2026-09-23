package com.seckill.mall.service;

public interface SeckillStockService {

    void preheatStock(Long seckillGoodsId);

    void preheatAllActiveStock();

    Long getRedisStock(Long seckillGoodsId);

    void syncStockToDB(Long seckillGoodsId);
}
