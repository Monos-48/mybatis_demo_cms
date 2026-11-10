package com.example.cms.common;

/**
 * 用户状态：0 禁用、1 正常、2 待审批。
 */
public enum UserStatus {
    DISABLED(0, "禁用"),
    ENABLED(1, "正常"),
    PENDING(2, "待审批");

    private final int code;
    private final String description;

    UserStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static UserStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public static String descriptionOf(Integer code) {
        UserStatus status = fromCode(code);
        return status == null ? "未知" : status.description;
    }
}
