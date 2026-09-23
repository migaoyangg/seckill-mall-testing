package com.seckill.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("seckill_goods")
public class SeckillGoods implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;
    private BigDecimal seckillPrice;
    private Integer stockCount;
    private BigDecimal originalPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer limitPerUser;
    private Integer status;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
