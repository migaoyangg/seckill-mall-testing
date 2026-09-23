package com.seckill.mall.interceptor;

import com.seckill.mall.annotation.AdminRequired;
import com.seckill.mall.annotation.LoginRequired;
import com.seckill.mall.common.Constants;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.utils.JwtUtils;
import com.seckill.mall.utils.RedisUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        LoginRequired loginRequired = handlerMethod.getMethodAnnotation(LoginRequired.class);
        AdminRequired adminRequired = handlerMethod.getMethodAnnotation(AdminRequired.class);
        if (loginRequired == null && adminRequired == null) {
            return true;
        }

        String token = extractToken(request);
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        Long userId;
        try {
            userId = jwtUtils.parseToken(token);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.TOKEN_INVALID);
        }

        String redisToken = redisUtils.get(Constants.REDIS_TOKEN_PREFIX + userId);
        if (redisToken == null || !redisToken.equals(token)) {
            throw new BusinessException(ResultCode.TOKEN_EXPIRED);
        }

        request.setAttribute("userId", userId);
        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(jwtUtils.getHeader());
        if (header != null && header.startsWith(jwtUtils.getPrefix())) {
            return header.substring(jwtUtils.getPrefix().length());
        }
        return null;
    }
}
