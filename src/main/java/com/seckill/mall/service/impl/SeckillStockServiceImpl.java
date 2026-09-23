package com.seckill.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.mall.common.Constants;
import com.seckill.mall.entity.SeckillGoods;
import com.seckill.mall.mapper.SeckillGoodsMapper;
import com.seckill.mall.service.SeckillStockService;
import com.seckill.mall.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillStockServiceImpl implements SeckillStockService {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void preheatStock(Long seckillGoodsId) {
        SeckillGoods seckillGoods = seckillGoodsMapper.selectById(seckillGoodsId);
        if (seckillGoods == null) {
            log.warn("秒杀商品不存在: id={}", seckillGoodsId);
            return;
        }

        redisUtils.preheatStock(seckillGoodsId, seckillGoods.getStockCount());

        String goodsKey = Constants.REDIS_SECKILL_GOODS_PREFIX + seckillGoodsId;
        redisUtils.set(goodsKey, seckillGoods);
        long secondsUntilEnd = Duration.between(LocalDateTime.now(), seckillGoods.getEndTime()).getSeconds();
        if (secondsUntilEnd > 0) {
            redisUtils.expire(goodsKey, secondsUntilEnd, TimeUnit.SECONDS);
        }

        log.info("秒杀商品预热完成: goodsId={}, stock={}", seckillGoodsId, seckillGoods.getStockCount());
    }

    @Override
    public void preheatAllActiveStock() {
        LambdaQueryWrapper<SeckillGoods> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillGoods::getStatus, Constants.SECKILL_IN_PROGRESS);
        List<SeckillGoods> activeGoods = seckillGoodsMapper.selectList(wrapper);

        for (SeckillGoods goods : activeGoods) {
            try {
                preheatStock(goods.getId());
            } catch (Exception e) {
                log.error("预热失败: goodsId={}", goods.getId(), e);
            }
        }
        log.info("批量预热完成: 共{}个活动", activeGoods.size());
    }

    @Override
    public Long getRedisStock(Long seckillGoodsId) {
        return redisUtils.getSeckillStock(seckillGoodsId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncStockToDB(Long seckillGoodsId) {
        Long redisStock = redisUtils.getSeckillStock(seckillGoodsId);
        if (redisStock == null) {
            log.warn("Redis中无库存数据: goodsId={}", seckillGoodsId);
            return;
        }

        SeckillGoods update = new SeckillGoods();
        update.setId(seckillGoodsId);
        update.setStockCount(redisStock.intValue());
        seckillGoodsMapper.updateById(update);

        redisUtils.delete(Constants.REDIS_SECKILL_STOCK_PREFIX + seckillGoodsId);
        log.info("库存同步完成: goodsId={}, remainingStock={}", seckillGoodsId, redisStock);
    }
}
