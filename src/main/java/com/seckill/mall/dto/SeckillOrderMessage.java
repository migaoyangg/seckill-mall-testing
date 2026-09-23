package com.seckill.mall.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String requestId;
    private String orderNo;
    private Long seckillGoodsId;
    private Long userId;
    private Integer retryTimes;

    public static SeckillOrderMessage of(String requestId, String orderNo,
                                         Long seckillGoodsId, Long userId) {
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setRequestId(requestId);
        message.setOrderNo(orderNo);
        message.setSeckillGoodsId(seckillGoodsId);
        message.setUserId(userId);
        message.setRetryTimes(0);
        return message;
    }

    public void increaseRetryTimes() {
        this.retryTimes = this.retryTimes == null ? 1 : this.retryTimes + 1;
    }
}
