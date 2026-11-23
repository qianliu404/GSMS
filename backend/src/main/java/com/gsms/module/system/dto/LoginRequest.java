package com.gsms.module.system.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LoginRequest {

    @NotBlank(message = "登录名不能为空")
    private String userId;

    @NotBlank(message = "密码不能为空")
    private String password;
}
