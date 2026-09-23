package com.seckill.mall.controller;

import com.seckill.mall.annotation.LoginRequired;
import com.seckill.mall.common.PageResult;
import com.seckill.mall.common.Result;
import com.seckill.mall.service.OrderService;
import com.seckill.mall.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@Tag(name = "订单模块", description = "订单创建、查询、支付、取消")
@RestController
@RequestMapping("/order")
@Validated
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单")
    @LoginRequired
    @PostMapping("/create")
    public Result<String> createOrder(@RequestParam Long productId,
                                      @RequestParam(defaultValue = "1") @Min(value = 1, message = "购买数量必须大于0") Integer quantity,
                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String orderNo = orderService.createOrder(userId, productId, quantity);
        return Result.success("下单成功", orderNo);
    }

    @Operation(summary = "查询订单详情")
    @LoginRequired
    @GetMapping("/detail/{orderNo}")
    public Result<OrderVO> getOrderDetail(@PathVariable String orderNo,
                                          HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        OrderVO vo = orderService.getOrderDetail(orderNo, userId);
        return Result.success(vo);
    }

    @Operation(summary = "用户订单列表")
    @LoginRequired
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> listOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        PageResult<OrderVO> result = orderService.listUserOrders(userId, status, page, size);
        return Result.success(result);
    }

    @Operation(summary = "取消订单")
    @LoginRequired
    @PostMapping("/cancel/{orderNo}")
    public Result<Void> cancelOrder(@PathVariable String orderNo,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        orderService.cancelOrder(orderNo, userId);
        return Result.success();
    }

    @Operation(summary = "支付订单 (模拟)")
    @LoginRequired
    @PostMapping("/pay/{orderNo}")
    public Result<Void> payOrder(@PathVariable String orderNo,
                                 @RequestParam(defaultValue = "1") Integer payType,
                                 HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        orderService.payOrder(orderNo, userId, payType);
        return Result.success();
    }

    @Operation(summary = "支付回调 (模拟幂等)")
    @LoginRequired
    @PostMapping("/pay/callback/{orderNo}")
    public Result<Void> payCallback(@PathVariable String orderNo,
                                    @RequestParam(defaultValue = "1") Integer payType,
                                    @RequestParam String transactionNo,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        orderService.payCallback(orderNo, userId, payType, transactionNo);
        return Result.success();
    }

    @Operation(summary = "申请退款 (模拟)")
    @LoginRequired
    @PostMapping("/refund/{orderNo}")
    public Result<Void> refundOrder(@PathVariable String orderNo,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        orderService.refundOrder(orderNo, userId);
        return Result.success();
    }
}
