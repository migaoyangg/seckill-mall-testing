package com.seckill.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer payType;
    private LocalDateTime payTime;
    private String transactionNo;
    private Integer status;
    private Integer isSeckill;
    private Long seckillGoodsId;
    private LocalDateTime refundTime;
    private String remark;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
