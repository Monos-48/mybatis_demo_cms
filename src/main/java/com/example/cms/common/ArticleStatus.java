package com.example.cms.common;

/**
 * 文章状态枚举。
 * 状态流转：草稿 -> 待审核 -> 通过/驳回；通过 -> 已发布；已发布 <-> 已下架。
 */
public enum ArticleStatus {
    DRAFT(0, "草稿"),
    PENDING(1, "待审核"),
    APPROVED(2, "审核通过"),
    REJECTED(3, "已驳回"),
    PUBLISHED(4, "已发布"),
    OFFLINE(5, "已下架");

    private final int code;
    private final String description;

    ArticleStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ArticleStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ArticleStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public static String descriptionOf(Integer code) {
        ArticleStatus status = fromCode(code);
        return status == null ? "未知" : status.description;
    }
}
