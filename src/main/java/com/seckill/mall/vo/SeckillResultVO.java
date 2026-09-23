package com.seckill.mall.vo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Data
@Schema(description = "秒杀结果")
public class SeckillResultVO implements Serializable {

    @Schema(description = "是否成功")
    private Boolean success;

    @Schema(description = "结果描述")
    private String message;

    @Schema(description = "请求ID, 用于查询异步下单结果")
    private String requestId;

    @Schema(description = "处理状态: PROCESSING-处理中, SUCCESS-成功, FAIL-失败")
    private String status;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "剩余库存")
    private Integer remainStock;

    public static SeckillResultVO fail(String message) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setSuccess(false);
        vo.setMessage(message);
        vo.setStatus("FAIL");
        return vo;
    }

    public static SeckillResultVO processing(String requestId, String orderNo, Integer remainStock) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setSuccess(true);
        vo.setMessage("秒杀请求已受理, 正在创建订单");
        vo.setRequestId(requestId);
        vo.setStatus("PROCESSING");
        vo.setOrderNo(orderNo);
        vo.setRemainStock(remainStock);
        return vo;
    }

    public static SeckillResultVO success(String orderNo, Integer remainStock) {
        SeckillResultVO vo = new SeckillResultVO();
        vo.setSuccess(true);
        vo.setMessage("秒杀成功");
        vo.setStatus("SUCCESS");
        vo.setOrderNo(orderNo);
        vo.setRemainStock(remainStock);
        return vo;
    }

    public static SeckillResultVO success(String requestId, String orderNo, Integer remainStock) {
        SeckillResultVO vo = success(orderNo, remainStock);
        vo.setRequestId(requestId);
        return vo;
    }
}
