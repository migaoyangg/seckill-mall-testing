package com.seckill.mall.vo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "秒杀商品信息")
public class SeckillGoodsVO implements Serializable {

    @Schema(description = "秒杀商品ID")
    private Long id;

    @Schema(description = "关联商品ID")
    private Long productId;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品图片")
    private String productImage;

    @Schema(description = "秒杀价格")
    private BigDecimal seckillPrice;

    @Schema(description = "原价")
    private BigDecimal originalPrice;

    @Schema(description = "折扣")
    private String discount;

    @Schema(description = "秒杀总库存")
    private Integer stockCount;

    @Schema(description = "剩余库存")
    private Integer remainStock;

    @Schema(description = "每人限购数量")
    private Integer limitPerUser;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "活动状态描述")
    private String statusDesc;
}
