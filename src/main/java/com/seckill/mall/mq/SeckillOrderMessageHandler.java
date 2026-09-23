package com.seckill.mall.mq;

import com.seckill.mall.dto.SeckillOrderMessage;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.service.OrderService;
import com.seckill.mall.utils.RedisUtils;
import com.seckill.mall.vo.SeckillResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderMessageHandler {

    private static final int MAX_RETRY_TIMES = 3;

    private final RedisUtils redisUtils;
    private final OrderService orderService;
    private final SeckillOrderProducer seckillOrderProducer;

    public void handle(SeckillOrderMessage message) {
        try {
            String orderNo = orderService.createSeckillOrder(
                    message.getSeckillGoodsId(), message.getUserId(), message.getOrderNo());
            redisUtils.setSeckillResult(
                    message.getRequestId(), SeckillResultVO.success(message.getRequestId(), orderNo, null));
            log.info("秒杀异步落单成功: requestId={}, orderNo={}, userId={}, goodsId={}",
                    message.getRequestId(), orderNo, message.getUserId(), message.getSeckillGoodsId());
        } catch (BusinessException e) {
            failOrRetry(message, e.getMessage(), e);
        } catch (Exception e) {
            failOrRetry(message, "订单创建异常, 请稍后查看结果", e);
        }
    }

    private void failOrRetry(SeckillOrderMessage message, String failMessage, Exception e) {
        message.increaseRetryTimes();
        if (message.getRetryTimes() < MAX_RETRY_TIMES) {
            seckillOrderProducer.send(message);
            redisUtils.setSeckillResult(message.getRequestId(),
                    SeckillResultVO.processing(message.getRequestId(), message.getOrderNo(), null));
            log.warn("秒杀异步落单失败, 已重试入队: requestId={}, retryTimes={}",
                    message.getRequestId(), message.getRetryTimes(), e);
            return;
        }

        redisUtils.rollbackSeckillDeduct(message.getSeckillGoodsId(), message.getUserId());
        redisUtils.deleteSeckillRepeatFlag(message.getSeckillGoodsId(), message.getUserId());
        SeckillResultVO result = SeckillResultVO.fail(failMessage);
        result.setRequestId(message.getRequestId());
        result.setOrderNo(message.getOrderNo());
        redisUtils.setSeckillResult(message.getRequestId(), result);
        log.error("秒杀异步落单最终失败, 已回滚预扣: requestId={}, userId={}, goodsId={}",
                message.getRequestId(), message.getUserId(), message.getSeckillGoodsId(), e);
    }
}
