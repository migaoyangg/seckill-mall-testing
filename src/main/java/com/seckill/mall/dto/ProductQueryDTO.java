package com.seckill.mall.dto;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "商品查询参数")
public class ProductQueryDTO {

    @Schema(description = "搜索关键词")
    private String keyword;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "最低价格")
    private Double minPrice;

    @Schema(description = "最高价格")
    private Double maxPrice;

    @Schema(description = "排序字段")
    private String sortBy;

    @Schema(description = "排序方式")
    private String sortOrder;

    @Schema(description = "页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer size = 10;
}
