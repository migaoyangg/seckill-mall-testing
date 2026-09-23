package com.seckill.mall.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "用户登录请求参数")
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "admin")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "通过环境变量配置的测试密码")
    private String password;
}
