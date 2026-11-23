package com.gsms.module.system.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private long expiresIn;
    private UserInfoVO user;
}
