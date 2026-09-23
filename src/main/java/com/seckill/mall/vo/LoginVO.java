package com.seckill.mall.vo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Data
@Schema(description = "登录响应")
public class LoginVO implements Serializable {

    @Schema(description = "JWT Token")
    private String token;

    @Schema(description = "用户信息")
    private UserVO user;
}
