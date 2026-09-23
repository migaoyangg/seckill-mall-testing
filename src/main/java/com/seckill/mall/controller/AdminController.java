package com.seckill.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.mall.annotation.AdminRequired;
import com.seckill.mall.common.PageResult;
import com.seckill.mall.common.Result;
import com.seckill.mall.dto.ProductDTO;
import com.seckill.mall.dto.ProductQueryDTO;
import com.seckill.mall.dto.SeckillGoodsDTO;
import com.seckill.mall.entity.User;
import com.seckill.mall.mapper.UserMapper;
import com.seckill.mall.service.OrderService;
import com.seckill.mall.service.ProductService;
import com.seckill.mall.service.SeckillService;
import com.seckill.mall.vo.OrderVO;
import com.seckill.mall.vo.ProductVO;
import com.seckill.mall.vo.SeckillGoodsVO;
import com.seckill.mall.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "管理员模块", description = "商品管理、订单管理、秒杀管理、用户管理")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final OrderService orderService;
    private final SeckillService seckillService;
    private final UserMapper userMapper;

    // ==================== 商品管理 ====================

    @Operation(summary = "商品列表 (管理员)")
    @AdminRequired
    @GetMapping("/product/list")
    public Result<PageResult<ProductVO>> listProducts(ProductQueryDTO queryDTO) {
        PageResult<ProductVO> result = productService.listAdminProducts(queryDTO);
        return Result.success(result);
    }

    @Operation(summary = "新增商品")
    @AdminRequired
    @PostMapping("/product/add")
    public Result<Void> addProduct(@Valid @RequestBody ProductDTO dto) {
        productService.addProduct(dto);
        return Result.success();
    }

    @Operation(summary = "修改商品")
    @AdminRequired
    @PutMapping("/product/update")
    public Result<Void> updateProduct(@Valid @RequestBody ProductDTO dto) {
        productService.updateProduct(dto);
        return Result.success();
    }

    @Operation(summary = "删除商品")
    @AdminRequired
    @DeleteMapping("/product/delete/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    @Operation(summary = "商品上架/下架")
    @AdminRequired
    @PutMapping("/product/status/{id}/{status}")
    public Result<Void> updateProductStatus(@PathVariable Long id,
                                            @PathVariable Integer status) {
        productService.updateProductStatus(id, status);
        return Result.success();
    }

    // ==================== 订单管理 ====================

    @Operation(summary = "订单列表 (管理员)")
    @AdminRequired
    @GetMapping("/order/list")
    public Result<PageResult<OrderVO>> listOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<OrderVO> result = orderService.listUserOrders(null, status, page, size);
        return Result.success(result);
    }

    @Operation(summary = "订单发货")
    @AdminRequired
    @PutMapping("/order/ship/{orderNo}")
    public Result<Void> shipOrder(@PathVariable String orderNo) {
        orderService.shipOrder(orderNo);
        return Result.success();
    }

    // ==================== 秒杀管理 ====================

    @Operation(summary = "秒杀活动列表")
    @AdminRequired
    @GetMapping("/seckill/list")
    public Result<PageResult<SeckillGoodsVO>> listSeckillGoods(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResult<SeckillGoodsVO> result = seckillService.listSeckillGoods(page, size);
        return Result.success(result);
    }

    @Operation(summary = "创建秒杀活动")
    @AdminRequired
    @PostMapping("/seckill/create")
    public Result<Void> createSeckillGoods(@Valid @RequestBody SeckillGoodsDTO dto) {
        seckillService.createSeckillGoods(dto);
        return Result.success();
    }

    @Operation(summary = "修改秒杀活动")
    @AdminRequired
    @PutMapping("/seckill/update")
    public Result<Void> updateSeckillGoods(@Valid @RequestBody SeckillGoodsDTO dto) {
        seckillService.updateSeckillGoods(dto);
        return Result.success();
    }

    @Operation(summary = "删除秒杀活动")
    @AdminRequired
    @DeleteMapping("/seckill/delete/{id}")
    public Result<Void> deleteSeckillGoods(@PathVariable Long id) {
        seckillService.deleteSeckillGoods(id);
        return Result.success();
    }

    @Operation(summary = "更新秒杀活动状态")
    @AdminRequired
    @PutMapping("/seckill/status/{id}/{status}")
    public Result<Void> updateSeckillStatus(@PathVariable Long id,
                                            @PathVariable Integer status) {
        seckillService.updateSeckillStatus(id, status);
        return Result.success();
    }

    // ==================== 用户管理 ====================

    @Operation(summary = "用户列表")
    @AdminRequired
    @GetMapping("/user/list")
    public Result<PageResult<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> result = userMapper.selectPage(pageParam, wrapper);

        List<UserVO> voList = result.getRecords().stream()
                .map(user -> {
                    UserVO vo = new UserVO();
                    vo.setId(user.getId());
                    vo.setUsername(user.getUsername());
                    vo.setNickname(user.getNickname());
                    vo.setPhone(user.getPhone());
                    vo.setEmail(user.getEmail());
                    vo.setRole(user.getRole());
                    vo.setCreateTime(user.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());

        return Result.success(new PageResult<>(result.getTotal(), voList, page, size));
    }

    @Operation(summary = "禁用/启用用户")
    @AdminRequired
    @PutMapping("/user/status/{userId}/{status}")
    public Result<Void> updateUserStatus(@PathVariable Long userId,
                                         @PathVariable Integer status) {
        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        userMapper.updateById(update);
        return Result.success();
    }
}
