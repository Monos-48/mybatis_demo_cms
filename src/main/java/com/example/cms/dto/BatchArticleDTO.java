package com.example.cms.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 文章批量操作请求。
 */
public class BatchArticleDTO {

    @NotEmpty(message = "请选择至少一篇文章")
    @Size(max = 100, message = "单次最多批量操作 100 篇文章")
    private List<Long> articleIds;

    public List<Long> getArticleIds() {
        return articleIds;
    }

    public void setArticleIds(List<Long> articleIds) {
        this.articleIds = articleIds;
    }
}
