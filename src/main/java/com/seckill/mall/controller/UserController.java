package com.seckill.mall.controller;

import com.seckill.mall.annotation.LoginRequired;
import com.seckill.mall.common.Result;
import com.seckill.mall.dto.LoginDTO;
import com.seckill.mall.dto.PasswordDTO;
import com.seckill.mall.dto.RegisterDTO;
import com.seckill.mall.service.UserService;
import com.seckill.mall.vo.LoginVO;
import com.seckill.mall.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户模块", description = "用户注册、登录、个人信息管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto,
                                 HttpServletRequest request) {
        String ip = getClientIp(request);
        LoginVO loginVO = userService.login(dto, ip);
        return Result.success(loginVO);
    }

    @Operation(summary = "退出登录")
    @LoginRequired
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.logout(userId);
        return Result.success();
    }

    @Operation(summary = "获取个人信息")
    @LoginRequired
    @GetMapping("/info")
    public Result<UserVO> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserVO userVO = userService.getUserInfo(userId);
        return Result.success(userVO);
    }

    @Operation(summary = "修改密码")
    @LoginRequired
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody PasswordDTO dto,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.changePassword(userId, dto);
        return Result.success();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
