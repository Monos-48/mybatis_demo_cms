package com.example.cms.interceptor;

import com.example.cms.common.LoginUser;
import com.example.cms.common.ResultVO;
import com.example.cms.entity.SysUser;
import com.example.cms.mapper.SysPermissionMapper;
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
import java.util.HashSet;

/**
 * 登录拦截器：校验 Session，实时确认账号状态，并刷新会话中的角色与权限。
 * <p>角色/权限每个请求都从数据库重新加载，避免管理员降权后旧会话仍按旧角色
 * 通过业务层的数据权限判断（如编辑/删除他人文章）。</p>
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final ObjectMapper objectMapper;

    public LoginInterceptor(SysUserMapper userMapper, SysPermissionMapper permissionMapper,
                            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
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

        // 以数据库为准刷新身份快照：接口权限与业务层数据权限使用同一时点的角色/权限
        loginUser.setNickname(user.getNickname());
        loginUser.setRoles(new HashSet<>(userMapper.selectRoleCodesByUserId(user.getId())));
        loginUser.setPermissions(new HashSet<>(
                permissionMapper.selectPermissionCodesByUserId(user.getId())));
        request.getSession().setAttribute(SecurityUtils.SESSION_USER_KEY, loginUser);
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
