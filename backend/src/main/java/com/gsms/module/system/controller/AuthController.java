package com.gsms.module.system.controller;

import com.gsms.common.R;
import com.gsms.module.system.dto.LoginRequest;
import com.gsms.module.system.dto.LoginResponse;
import com.gsms.module.system.dto.UserInfoVO;
import com.gsms.module.system.service.AuthService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @GetMapping("/users/me")
    public R<UserInfoVO> me() {
        return R.ok(authService.currentUser());
    }
}
