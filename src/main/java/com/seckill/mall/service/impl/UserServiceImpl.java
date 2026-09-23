package com.seckill.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.mall.common.Constants;
import com.seckill.mall.common.ResultCode;
import com.seckill.mall.dto.LoginDTO;
import com.seckill.mall.dto.PasswordDTO;
import com.seckill.mall.dto.RegisterDTO;
import com.seckill.mall.entity.User;
import com.seckill.mall.exception.BusinessException;
import com.seckill.mall.mapper.UserMapper;
import com.seckill.mall.service.UserService;
import com.seckill.mall.utils.JwtUtils;
import com.seckill.mall.utils.Md5Utils;
import com.seckill.mall.utils.RedisUtils;
import com.seckill.mall.vo.LoginVO;
import com.seckill.mall.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.USER_EXISTS);
        }

        if (dto.getPhone() != null) {
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getPhone, dto.getPhone());
            if (userMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ResultCode.PHONE_EXISTS);
            }
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(Md5Utils.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setRole(Constants.ROLE_USER);
        user.setStatus(Constants.STATUS_NORMAL);
        userMapper.insert(user);
        log.info("用户注册成功: username={}", dto.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto, String ip) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (!Md5Utils.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() == Constants.STATUS_DISABLED) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }

        String token = jwtUtils.generateToken(user.getId());
        redisUtils.set(Constants.REDIS_TOKEN_PREFIX + user.getId(), token, 2, TimeUnit.HOURS);
        redisUtils.set(Constants.REDIS_USER_INFO_PREFIX + user.getId(), user, 30, TimeUnit.MINUTES);

        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setLastLoginTime(LocalDateTime.now());
        updateUser.setLastLoginIp(ip);
        userMapper.updateById(updateUser);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(convertToUserVO(user));
        log.info("用户登录成功: username={}", dto.getUsername());
        return loginVO;
    }

    @Override
    public void logout(Long userId) {
        redisUtils.delete(Constants.REDIS_TOKEN_PREFIX + userId);
        redisUtils.delete(Constants.REDIS_USER_INFO_PREFIX + userId);
        log.info("用户退出登录: userId={}", userId);
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        String redisKey = Constants.REDIS_USER_INFO_PREFIX + userId;
        User user = redisUtils.getObject(redisKey, User.class);

        if (user == null) {
            user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND);
            }
            redisUtils.set(redisKey, user, 30, TimeUnit.MINUTES);
        }
        return convertToUserVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, PasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!Md5Utils.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED, "原密码错误");
        }

        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPassword(Md5Utils.encode(dto.getNewPassword()));
        userMapper.updateById(updateUser);

        redisUtils.delete(Constants.REDIS_TOKEN_PREFIX + userId);
        redisUtils.delete(Constants.REDIS_USER_INFO_PREFIX + userId);
        log.info("用户修改密码成功: userId={}", userId);
    }

    private UserVO convertToUserVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
