package com.seckill.mall.controller;

import com.seckill.mall.annotation.AdminRequired;
import com.seckill.mall.common.PageResult;
import com.seckill.mall.common.Result;
import com.seckill.mall.dto.ProductDTO;
import com.seckill.mall.dto.ProductQueryDTO;
import com.seckill.mall.service.ProductService;
import com.seckill.mall.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "商品模块", description = "商品查询、搜索、管理")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "商品列表 (分页)")
    @GetMapping("/list")
    public Result<PageResult<ProductVO>> listProducts(ProductQueryDTO queryDTO) {
        PageResult<ProductVO> result = productService.listProducts(queryDTO);
        return Result.success(result);
    }

    @Operation(summary = "商品详情")
    @GetMapping("/detail/{id}")
    public Result<ProductVO> getProductDetail(@PathVariable Long id) {
        ProductVO vo = productService.getProductDetail(id);
        return Result.success(vo);
    }

    @Operation(summary = "商品搜索")
    @GetMapping("/search")
    public Result<PageResult<ProductVO>> searchProducts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<ProductVO> result = productService.searchProducts(keyword, page, size);
        return Result.success(result);
    }

    @Operation(summary = "新增商品 (管理员)")
    @AdminRequired
    @PostMapping("/admin/add")
    public Result<Void> addProduct(@Valid @RequestBody ProductDTO dto) {
        productService.addProduct(dto);
        return Result.success();
    }

    @Operation(summary = "修改商品 (管理员)")
    @AdminRequired
    @PutMapping("/admin/update")
    public Result<Void> updateProduct(@Valid @RequestBody ProductDTO dto) {
        productService.updateProduct(dto);
        return Result.success();
    }

    @Operation(summary = "删除商品 (管理员)")
    @AdminRequired
    @DeleteMapping("/admin/delete/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    @Operation(summary = "商品上架/下架 (管理员)")
    @AdminRequired
    @PutMapping("/admin/status/{id}/{status}")
    public Result<Void> updateProductStatus(@PathVariable Long id,
                                            @PathVariable Integer status) {
        productService.updateProductStatus(id, status);
        return Result.success();
    }
}
