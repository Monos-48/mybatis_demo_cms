package com.example.cms.service;

import com.example.cms.dto.LoginDTO;
import com.example.cms.dto.RegisterDTO;
import com.example.cms.vo.LoginVO;
import com.example.cms.vo.UserVO;

import javax.servlet.http.HttpServletRequest;

/**
 * 认证与注册业务。
 */
public interface AuthService {

    LoginVO login(LoginDTO dto, HttpServletRequest request);

    UserVO register(RegisterDTO dto);

    LoginVO currentUser();

    void logout();
}
