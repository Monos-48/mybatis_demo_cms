package com.example.cms.controller;

import com.example.cms.annotation.Log;
import com.example.cms.common.ResultVO;
import com.example.cms.dto.LoginDTO;
import com.example.cms.dto.RegisterDTO;
import com.example.cms.service.AuthService;
import com.example.cms.vo.LoginVO;
import com.example.cms.vo.UserVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 认证接口：注册、登录、退出、当前用户。
 */
@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Log(module = "认证管理", operation = "用户注册")
    public ResultVO<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return ResultVO.success("注册成功，请等待管理员审批", authService.register(dto));
    }

    @PostMapping("/login")
    @Log(module = "认证管理", operation = "用户登录")
    public ResultVO<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return ResultVO.success("登录成功", authService.login(dto, request));
    }

    @PostMapping("/logout")
    @Log(module = "认证管理", operation = "退出登录")
    public ResultVO<Void> logout() {
        authService.logout();
        return ResultVO.success();
    }

    @GetMapping("/me")
    public ResultVO<LoginVO> me() {
        return ResultVO.success(authService.currentUser());
    }
}
