package com.example.cms.util;

import com.example.cms.common.LoginUser;
import com.example.cms.exception.UnauthorizedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * 当前登录用户工具类，基于 HttpSession。
 */
public final class SecurityUtils {

    public static final String SESSION_USER_KEY = "CMS_LOGIN_USER";

    private SecurityUtils() {
    }

    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    public static HttpSession getSession(boolean create) {
        HttpServletRequest request = getRequest();
        return request == null ? null : request.getSession(create);
    }

    public static LoginUser getLoginUser() {
        HttpSession session = getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_USER_KEY);
        return value instanceof LoginUser ? (LoginUser) value : null;
    }

    public static LoginUser requireLoginUser() {
        LoginUser user = getLoginUser();
        if (user == null) {
            throw new UnauthorizedException();
        }
        return user;
    }

    public static Long getCurrentUserId() {
        LoginUser user = getLoginUser();
        return user == null ? null : user.getId();
    }

    public static boolean isManager() {
        LoginUser user = getLoginUser();
        return user != null && user.isManager();
    }

    public static void clearSession() {
        HttpSession session = getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
