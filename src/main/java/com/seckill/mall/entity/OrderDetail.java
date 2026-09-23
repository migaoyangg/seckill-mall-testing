package com.seckill.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_detail")
public class OrderDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal productPrice;
    private Integer quantity;
    private BigDecimal subtotal;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
