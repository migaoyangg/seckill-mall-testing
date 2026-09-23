package com.seckill.mall.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "秒杀商品请求参数")
public class SeckillGoodsDTO {

    @Schema(description = "秒杀商品ID (修改时必填)")
    private Long id;

    @NotNull(message = "关联商品不能为空")
    @Schema(description = "关联商品ID")
    private Long productId;

    @NotNull(message = "秒杀价格不能为空")
    @DecimalMin(value = "0.01", message = "秒杀价格必须大于0")
    @Schema(description = "秒杀价格")
    private BigDecimal seckillPrice;

    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 1, message = "秒杀库存必须大于0")
    @Schema(description = "秒杀库存")
    private Integer stockCount;

    @NotNull(message = "开始时间不能为空")
    @Schema(description = "秒杀开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @Schema(description = "秒杀结束时间")
    private LocalDateTime endTime;

    @Min(value = 1, message = "限购数量必须大于0")
    @Schema(description = "每人限购数量", defaultValue = "1")
    private Integer limitPerUser = 1;
}
