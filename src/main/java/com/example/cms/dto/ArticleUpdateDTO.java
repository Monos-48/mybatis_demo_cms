package com.example.cms.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 编辑文章请求，version 用于乐观锁并发控制。
 */
public class ArticleUpdateDTO {

    @NotBlank(message = "标题不能为空")
    @Size(min = 2, max = 100, message = "标题长度须为 2-100 个字符")
    private String title;

    @Size(max = 500, message = "摘要长度不能超过 500 个字符")
    private String summary;

    @NotBlank(message = "正文不能为空")
    @Size(min = 10, max = 100000, message = "正文长度须为 10-100000 个字符")
    private String content;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotNull(message = "版本号不能为空，请刷新后重试")
    private Integer version;

    private Boolean submitForReview = Boolean.FALSE;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getSubmitForReview() {
        return submitForReview;
    }

    public void setSubmitForReview(Boolean submitForReview) {
        this.submitForReview = submitForReview;
    }
}
