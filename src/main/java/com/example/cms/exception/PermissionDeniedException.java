package com.example.cms.exception;

/**
 * 已登录但缺少接口所需权限。
 */
public class PermissionDeniedException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public PermissionDeniedException(String message) {
        super(403, message);
    }
}
