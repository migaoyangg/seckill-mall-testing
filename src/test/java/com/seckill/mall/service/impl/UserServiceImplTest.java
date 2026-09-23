package com.seckill.mall.service.impl;

import com.seckill.mall.common.Constants;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.dto.LoginDTO;
import com.seckill.mall.entity.User;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.mapper.UserMapper;
import com.seckill.mall.utils.JwtUtils;
import com.seckill.mall.utils.Md5Utils;
import com.seckill.mall.utils.RedisUtils;
import com.seckill.mall.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RedisUtils redisUtils;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userMapper, jwtUtils, redisUtils);
    }

    @Test
    void loginStoresTokenAndReturnsUserWithoutPassword() {
        User user = user(7L, "alice", Md5Utils.encode("secret"), Constants.STATUS_NORMAL);
        LoginDTO dto = login("alice", "secret");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtUtils.generateToken(7L)).thenReturn("jwt-token");

        LoginVO result = service.login(dto, "127.0.0.1");

        assertEquals("jwt-token", result.getToken());
        assertEquals(7L, result.getUser().getId());
        assertEquals("alice", result.getUser().getUsername());
        assertNotNull(result.getUser());
        verify(redisUtils).set(eq(Constants.REDIS_TOKEN_PREFIX + 7L), eq("jwt-token"), eq(2L), eq(TimeUnit.HOURS));
        verify(redisUtils).set(eq(Constants.REDIS_USER_INFO_PREFIX + 7L), eq(user), eq(30L), eq(TimeUnit.MINUTES));
        verify(userMapper).updateById(any(User.class));
    }

    @Test
    void loginRejectsWrongPasswordWithoutGeneratingToken() {
        User user = user(7L, "alice", Md5Utils.encode("secret"), Constants.STATUS_NORMAL);
        when(userMapper.selectOne(any())).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(login("alice", "wrong"), "127.0.0.1"));

        assertEquals(ResultCode.LOGIN_FAILED.getCode(), exception.getCode());
        verify(jwtUtils, never()).generateToken(any());
        verify(redisUtils, never()).set(any(), any(), any(Long.class), any(TimeUnit.class));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void loginRejectsDisabledAccount() {
        User user = user(7L, "alice", Md5Utils.encode("secret"), Constants.STATUS_DISABLED);
        when(userMapper.selectOne(any())).thenReturn(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(login("alice", "secret"), "127.0.0.1"));

        assertEquals(ResultCode.ACCOUNT_DISABLED.getCode(), exception.getCode());
        verify(jwtUtils, never()).generateToken(any());
        verify(redisUtils, never()).set(any(), any(), any(Long.class), any(TimeUnit.class));
    }

    @Test
    void logoutDeletesTokenAndCachedUser() {
        service.logout(7L);

        verify(redisUtils).delete(Constants.REDIS_TOKEN_PREFIX + 7L);
        verify(redisUtils).delete(Constants.REDIS_USER_INFO_PREFIX + 7L);
    }

    private static LoginDTO login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    private static User user(Long id, String username, String password, Integer status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(username);
        user.setRole(Constants.ROLE_USER);
        user.setStatus(status);
        return user;
    }
}
