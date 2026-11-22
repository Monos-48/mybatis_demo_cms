package com.example.cms.common;

/**
 * 已发布文章的修订状态。
 * 修订流程：草稿 -> 待审核 -> 通过（替换线上内容）/ 驳回 -> 修改后重新提交。
 */
public enum RevisionStatus {
    DRAFT(0, "修订草稿"),
    PENDING(1, "待审核"),
    APPROVED(2, "审核通过"),
    REJECTED(3, "已驳回");

    private final int code;
    private final String description;

    RevisionStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static RevisionStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (RevisionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public static String descriptionOf(Integer code) {
        RevisionStatus status = fromCode(code);
        return status == null ? null : status.description;
    }
}
