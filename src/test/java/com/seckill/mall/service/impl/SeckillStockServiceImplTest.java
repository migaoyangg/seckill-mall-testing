package com.seckill.mall.service.impl;

import com.seckill.mall.common.Constants;
import com.seckill.mall.entity.SeckillGoods;
import com.seckill.mall.mapper.SeckillGoodsMapper;
import com.seckill.mall.utils.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillStockServiceImplTest {

    @Mock
    private SeckillGoodsMapper seckillGoodsMapper;
    @Mock
    private RedisUtils redisUtils;

    private SeckillStockServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SeckillStockServiceImpl(seckillGoodsMapper, redisUtils);
    }

    @Test
    void preheatStockSkipsMissingActivity() {
        when(seckillGoodsMapper.selectById(8L)).thenReturn(null);

        service.preheatStock(8L);

        verify(redisUtils, never()).preheatStock(anyLong(), any());
        verify(redisUtils, never()).set(any(), any());
    }

    @Test
    void preheatStockCachesStockGoodsAndActivityExpiry() {
        SeckillGoods goods = goods(8L, 12, LocalDateTime.now().plusMinutes(5));
        when(seckillGoodsMapper.selectById(8L)).thenReturn(goods);

        service.preheatStock(8L);

        verify(redisUtils).preheatStock(8L, 12);
        verify(redisUtils).set(Constants.REDIS_SECKILL_GOODS_PREFIX + 8L, goods);
        verify(redisUtils).expire(eq(Constants.REDIS_SECKILL_GOODS_PREFIX + 8L), anyLong(), any());
    }

    @Test
    void syncStockToDbDoesNothingWhenRedisStockIsAbsent() {
        when(redisUtils.getSeckillStock(8L)).thenReturn(null);

        service.syncStockToDB(8L);

        verify(seckillGoodsMapper, never()).updateById(any(SeckillGoods.class));
        verify(redisUtils, never()).delete(Constants.REDIS_SECKILL_STOCK_PREFIX + 8L);
    }

    @Test
    void syncStockToDbPersistsRemainingStockThenRemovesRedisKey() {
        when(redisUtils.getSeckillStock(8L)).thenReturn(7L);

        service.syncStockToDB(8L);

        ArgumentCaptor<SeckillGoods> goodsCaptor = ArgumentCaptor.forClass(SeckillGoods.class);
        verify(seckillGoodsMapper).updateById(goodsCaptor.capture());
        assertEquals(8L, goodsCaptor.getValue().getId());
        assertEquals(7, goodsCaptor.getValue().getStockCount());
        verify(redisUtils).delete(Constants.REDIS_SECKILL_STOCK_PREFIX + 8L);
    }

    private static SeckillGoods goods(Long id, Integer stock, LocalDateTime endTime) {
        SeckillGoods goods = new SeckillGoods();
        goods.setId(id);
        goods.setStockCount(stock);
        goods.setStatus(Constants.SECKILL_IN_PROGRESS);
        goods.setEndTime(endTime);
        return goods;
    }
}
