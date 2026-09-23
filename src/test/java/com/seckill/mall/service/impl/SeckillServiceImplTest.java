package com.seckill.mall.service.impl;

import com.seckill.mall.common.Constants;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.dto.SeckillOrderMessage;
import com.seckill.mall.entity.SeckillGoods;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.mapper.ProductMapper;
import com.seckill.mall.mapper.SeckillGoodsMapper;
import com.seckill.mall.mq.SeckillOrderProducer;
import com.seckill.mall.utils.RedisUtils;
import com.seckill.mall.vo.SeckillResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillServiceImplTest {

    @Mock
    private SeckillGoodsMapper seckillGoodsMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private RedisUtils redisUtils;

    @Mock
    private SeckillOrderProducer seckillOrderProducer;

    private SeckillServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SeckillServiceImpl(seckillGoodsMapper, productMapper, redisUtils, seckillOrderProducer);
    }

    @Test
    void doSeckillRejectsActivityThatHasNotStarted() {
        SeckillGoods activity = activity(LocalDateTime.now().plusMinutes(5), LocalDateTime.now().plusMinutes(10));
        when(redisUtils.getObject(Constants.REDIS_SECKILL_GOODS_PREFIX + 11L, SeckillGoods.class)).thenReturn(activity);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.doSeckill(11L, 22L));

        assertEquals(ResultCode.SECKILL_NOT_START.getCode(), exception.getCode());
        verify(redisUtils, never()).acquireSeckillRateLimit(any(), any(), any(Integer.class), any(Integer.class));
        verify(redisUtils, never()).executeSeckillDeduct(any(), any(), any());
    }

    @Test
    void doSeckillRejectsDuplicateRequestBeforeDeductingStock() {
        SeckillGoods activity = activity(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10));
        when(redisUtils.getObject(Constants.REDIS_SECKILL_GOODS_PREFIX + 11L, SeckillGoods.class)).thenReturn(activity);
        when(redisUtils.acquireSeckillRateLimit(11L, 22L, 1, 5)).thenReturn(true);
        when(redisUtils.setSeckillRepeatFlag(11L, 22L)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.doSeckill(11L, 22L));

        assertEquals(ResultCode.SECKILL_REPEAT.getCode(), exception.getCode());
        verify(redisUtils, never()).executeSeckillDeduct(any(), any(), any());
        verify(seckillOrderProducer, never()).send(any());
    }

    @Test
    void doSeckillDeletesRepeatFlagWhenStockIsEmpty() {
        SeckillGoods activity = activity(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10));
        when(redisUtils.getObject(Constants.REDIS_SECKILL_GOODS_PREFIX + 11L, SeckillGoods.class)).thenReturn(activity);
        when(redisUtils.acquireSeckillRateLimit(11L, 22L, 1, 5)).thenReturn(true);
        when(redisUtils.setSeckillRepeatFlag(11L, 22L)).thenReturn(true);
        when(redisUtils.executeSeckillDeduct(11L, 22L, activity.getLimitPerUser())).thenReturn(-1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.doSeckill(11L, 22L));

        assertEquals(ResultCode.SECKILL_STOCK_EMPTY.getCode(), exception.getCode());
        verify(redisUtils).deleteSeckillRepeatFlag(11L, 22L);
        verify(seckillOrderProducer, never()).send(any());
    }

    @Test
    void doSeckillPublishesProcessingResultAndOrderMessage() {
        SeckillGoods activity = activity(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10));
        when(redisUtils.getObject(Constants.REDIS_SECKILL_GOODS_PREFIX + 11L, SeckillGoods.class)).thenReturn(activity);
        when(redisUtils.acquireSeckillRateLimit(11L, 22L, 1, 5)).thenReturn(true);
        when(redisUtils.setSeckillRepeatFlag(11L, 22L)).thenReturn(true);
        when(redisUtils.executeSeckillDeduct(11L, 22L, activity.getLimitPerUser())).thenReturn(3L);

        SeckillResultVO result = service.doSeckill(11L, 22L);

        assertEquals("PROCESSING", result.getStatus());
        assertEquals(3, result.getRemainStock());
        assertNotNull(result.getRequestId());
        assertNotNull(result.getOrderNo());
        verify(redisUtils).setSeckillResult(eq(result.getRequestId()), eq(result));
        ArgumentCaptor<SeckillOrderMessage> messageCaptor = ArgumentCaptor.forClass(SeckillOrderMessage.class);
        verify(seckillOrderProducer).send(messageCaptor.capture());
        SeckillOrderMessage message = messageCaptor.getValue();
        assertEquals(result.getRequestId(), message.getRequestId());
        assertEquals(result.getOrderNo(), message.getOrderNo());
        assertEquals(11L, message.getSeckillGoodsId());
        assertEquals(22L, message.getUserId());
    }

    @Test
    void doSeckillRollsBackWhenProducerFails() {
        SeckillGoods activity = activity(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(10));
        when(redisUtils.getObject(Constants.REDIS_SECKILL_GOODS_PREFIX + 11L, SeckillGoods.class)).thenReturn(activity);
        when(redisUtils.acquireSeckillRateLimit(11L, 22L, 1, 5)).thenReturn(true);
        when(redisUtils.setSeckillRepeatFlag(11L, 22L)).thenReturn(true);
        when(redisUtils.executeSeckillDeduct(11L, 22L, activity.getLimitPerUser())).thenReturn(3L);
        org.mockito.Mockito.doThrow(new IllegalStateException("queue unavailable"))
                .when(seckillOrderProducer).send(any(SeckillOrderMessage.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.doSeckill(11L, 22L));

        assertEquals(ResultCode.SECKILL_FAIL.getCode(), exception.getCode());
        verify(redisUtils).rollbackSeckillDeduct(11L, 22L);
        verify(redisUtils).deleteSeckillRepeatFlag(11L, 22L);
    }

    private static SeckillGoods activity(LocalDateTime start, LocalDateTime end) {
        SeckillGoods activity = new SeckillGoods();
        activity.setId(11L);
        activity.setProductId(101L);
        activity.setStartTime(start);
        activity.setEndTime(end);
        activity.setStatus(Constants.SECKILL_IN_PROGRESS);
        activity.setLimitPerUser(1);
        activity.setStockCount(5);
        return activity;
    }
}
