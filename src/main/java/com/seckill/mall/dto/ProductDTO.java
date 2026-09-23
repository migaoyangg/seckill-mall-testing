package com.seckill.mall.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Data
@Schema(description = "商品请求参数")
public class ProductDTO {

    @Schema(description = "商品ID (修改时必填)")
    private Long id;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称最长128个字符")
    @Schema(description = "商品名称")
    private String name;

    @Size(max = 512, message = "商品描述最长512个字符")
    @Schema(description = "商品描述")
    private String description;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "商品价格必须大于0")
    @Schema(description = "商品价格")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能小于0")
    @Schema(description = "库存")
    private Integer stock;

    @NotNull(message = "分类不能为空")
    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "主图URL")
    private String mainImage;

    @Schema(description = "详情图JSON数组")
    private String detailImages;
}
