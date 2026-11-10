package com.example.cms.interceptor;

import com.example.cms.common.LoginUser;
import com.example.cms.common.ResultVO;
import com.example.cms.entity.SysUser;
import com.example.cms.mapper.SysUserMapper;
import com.example.cms.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器：校验 Session，并实时确认账号仍处于启用状态。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public LoginInterceptor(SysUserMapper userMapper, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            writeUnauthorized(request, response, "未登录或登录已过期");
            return false;
        }

        SysUser user = userMapper.selectById(loginUser.getId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            request.getSession().invalidate();
            writeUnauthorized(request, response, "账号已被禁用或删除，请重新登录");
            return false;
        }
        return true;
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ResultVO<Void> body = ResultVO.error(401, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
