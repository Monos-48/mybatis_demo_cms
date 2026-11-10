package com.example.cms.dto;

import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文章高级分页检索条件。排序字段在 Service 中做白名单校验。
 */
public class ArticleQueryDTO extends PageQuery {

    @Size(max = 100, message = "标题关键字长度不能超过 100")
    private String title;

    private Long categoryId;

    /** 是否包含所选分类的子孙分类，默认 true。 */
    private Boolean includeChildren = Boolean.TRUE;

    @Min(value = 0, message = "状态值不合法")
    @Max(value = 5, message = "状态值不合法")
    private Integer status;

    private Long authorId;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @Pattern(regexp = "^(createTime|updateTime|publishTime|viewCount|title)$",
            message = "排序字段仅支持 createTime、updateTime、publishTime、viewCount、title")
    private String sortField = "createTime";

    @Pattern(regexp = "^(asc|desc)$", message = "排序方向仅支持 asc 或 desc")
    private String sortOrder = "desc";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Boolean getIncludeChildren() {
        return includeChildren;
    }

    public void setIncludeChildren(Boolean includeChildren) {
        this.includeChildren = includeChildren;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    /**
     * 起始时间（含），供 SQL 使用索引范围查询。
     */
    public LocalDateTime getStartTime() {
        return startDate == null ? null : startDate.atStartOfDay();
    }

    /**
     * 结束时间（不含），取 endDate 次日零点。
     */
    public LocalDateTime getEndTime() {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
