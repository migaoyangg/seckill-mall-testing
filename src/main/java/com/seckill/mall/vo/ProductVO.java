package com.seckill.mall.vo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "商品信息")
public class ProductVO implements Serializable {

    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "商品价格")
    private BigDecimal price;

    @Schema(description = "库存")
    private Integer stock;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "主图URL")
    private String mainImage;

    @Schema(description = "详情图JSON数组")
    private String detailImages;

    @Schema(description = "销量")
    private Integer sales;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
