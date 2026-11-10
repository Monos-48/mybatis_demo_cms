package com.example.cms.common;

/**
 * 通用启用状态：0 停用、1 启用。
 */
public enum EnableStatus {
    DISABLED(0, "停用"),
    ENABLED(1, "启用");

    private final int code;
    private final String description;

    EnableStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
