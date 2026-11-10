package com.example.cms.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 文章审核请求。驳回时必须填写审核意见。
 */
public class ArticleAuditDTO {

    @NotNull(message = "审核结论不能为空")
    private Boolean approved;

    @Size(max = 500, message = "审核意见长度不能超过 500 个字符")
    private String remark;

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
