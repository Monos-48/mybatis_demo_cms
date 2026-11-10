package com.example.cms.exception;

/**
 * 未登录 / 会话过期。
 */
public class UnauthorizedException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedException() {
        super(401, "未登录或登录已过期");
    }

    public UnauthorizedException(String message) {
        super(401, message);
    }
}
