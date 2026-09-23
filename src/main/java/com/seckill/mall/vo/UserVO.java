package com.seckill.mall.vo;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "用户信息")
public class UserVO implements Serializable {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像URL")
    private String avatar;

    @Schema(description = "角色")
    private Integer role;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;
}
