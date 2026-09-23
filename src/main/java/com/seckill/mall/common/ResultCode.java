package com.seckill.mall.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),

    // 认证相关 1xxx
    UNAUTHORIZED(1001, "未登录或Token已过期"),
    FORBIDDEN(1002, "无权限访问"),
    TOKEN_INVALID(1003, "Token无效"),
    TOKEN_EXPIRED(1004, "Token已过期"),
    ACCOUNT_DISABLED(1005, "账号已被禁用"),
    LOGIN_FAILED(1006, "用户名或密码错误"),
    USER_EXISTS(1007, "用户名已存在"),
    PHONE_EXISTS(1008, "手机号已被注册"),

    // 参数相关 2xxx
    PARAM_ERROR(2001, "参数错误"),
    PARAM_MISS(2002, "缺少必要参数"),
    PARAM_TYPE_ERROR(2003, "参数类型错误"),

    // 业务相关 3xxx
    USER_NOT_FOUND(3001, "用户不存在"),
    PRODUCT_NOT_FOUND(3002, "商品不存在"),
    PRODUCT_OFF_SHELF(3003, "商品已下架"),
    ORDER_NOT_FOUND(3004, "订单不存在"),
    ORDER_STATUS_ERROR(3005, "订单状态异常"),
    ORDER_CANCEL_FAILED(3006, "订单取消失败"),

    // 秒杀相关 4xxx
    SECKILL_NOT_START(4001, "秒杀未开始"),
    SECKILL_ENDED(4002, "秒杀已结束"),
    SECKILL_STOCK_EMPTY(4003, "秒杀库存不足"),
    SECKILL_REPEAT(4004, "请勿重复秒杀"),
    SECKILL_LIMIT(4005, "超出限购数量"),
    SECKILL_FAIL(4006, "秒杀失败, 请重试"),

    // 库存相关 5xxx
    STOCK_NOT_ENOUGH(5001, "库存不足");

    private final Integer code;
    private final String message;
}
