package com.seckill.mall.service;

import com.seckill.mall.dto.LoginDTO;
import com.seckill.mall.dto.PasswordDTO;
import com.seckill.mall.dto.RegisterDTO;
import com.seckill.mall.vo.LoginVO;
import com.seckill.mall.vo.UserVO;

public interface UserService {

    void register(RegisterDTO dto);

    LoginVO login(LoginDTO dto, String ip);

    void logout(Long userId);

    UserVO getUserInfo(Long userId);

    void changePassword(Long userId, PasswordDTO dto);
}
