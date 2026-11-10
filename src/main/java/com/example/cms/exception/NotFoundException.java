package com.example.cms.exception;

/**
 * 资源不存在。
 */
public class NotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(404, message);
    }
}
