package com.example.cms.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cms.dto.PageQuery;

/**
 * 分页参数转换工具。
 */
public final class PageUtils {

    private PageUtils() {
    }

    public static <T> Page<T> toPage(PageQuery query) {
        int pageNum = query.getPageNum() == null ? 1 : Math.max(1, query.getPageNum());
        int pageSize = query.getPageSize() == null ? 10 : Math.min(100, Math.max(1, query.getPageSize()));
        return new Page<>(pageNum, pageSize);
    }
}
