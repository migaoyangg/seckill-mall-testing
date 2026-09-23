package com.seckill.mall.interceptor;

import com.seckill.mall.annotation.AdminRequired;
import com.seckill.mall.common.Constants;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.entity.User;
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
public class AdminInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        AdminRequired adminRequired = handlerMethod.getMethodAnnotation(AdminRequired.class);
        if (adminRequired == null) {
            return true;
        }

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            userId = authenticate(request);
            request.setAttribute("userId", userId);
        }

        User user = redisUtils.getObject(Constants.REDIS_USER_INFO_PREFIX + userId, User.class);
        if (user == null || user.getRole() != Constants.ROLE_ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        return true;
    }

    private Long authenticate(HttpServletRequest request) {
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

        return userId;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(jwtUtils.getHeader());
        if (header != null && header.startsWith(jwtUtils.getPrefix())) {
            return header.substring(jwtUtils.getPrefix().length());
        }
        return null;
    }
}
