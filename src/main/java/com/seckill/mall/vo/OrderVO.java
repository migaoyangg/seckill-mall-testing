package com.seckill.mall.vo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "订单信息")
public class OrderVO implements Serializable {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "订单总金额")
    private BigDecimal totalAmount;

    @Schema(description = "实付金额")
    private BigDecimal payAmount;

    @Schema(description = "支付方式")
    private Integer payType;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @Schema(description = "支付流水号")
    private String transactionNo;

    @Schema(description = "订单状态")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "是否秒杀订单")
    private Integer isSeckill;

    @Schema(description = "秒杀商品ID")
    private Long seckillGoodsId;

    @Schema(description = "退款时间")
    private LocalDateTime refundTime;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品图片")
    private String productImage;

    @Schema(description = "商品单价")
    private BigDecimal productPrice;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
