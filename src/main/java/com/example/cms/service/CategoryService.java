package com.example.cms.service;

import com.example.cms.dto.CategorySaveDTO;
import com.example.cms.vo.CategoryVO;

import java.util.List;
import java.util.Set;

/**
 * 内容分类业务。
 */
public interface CategoryService {

    List<CategoryVO> tree(boolean enabledOnly);

    CategoryVO getDetail(Long id);

    /** 分类自身及所有祖先均启用时才视为有效启用。 */
    boolean isEffectivelyEnabled(Long categoryId);

    /** 所有有效启用分类的 ID 集合，供公开文章查询过滤。 */
    Set<Long> effectiveEnabledCategoryIds();

    Long create(CategorySaveDTO dto);

    void update(Long id, CategorySaveDTO dto);

    void updateStatus(Long id, Integer status);

    void delete(Long id);
}
