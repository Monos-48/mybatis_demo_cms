package com.example.cms.service;

import com.example.cms.dto.CategorySaveDTO;
import com.example.cms.vo.CategoryVO;

import java.util.List;

/**
 * 内容分类业务。
 */
public interface CategoryService {

    List<CategoryVO> tree(boolean enabledOnly);

    CategoryVO getDetail(Long id);

    Long create(CategorySaveDTO dto);

    void update(Long id, CategorySaveDTO dto);

    void updateStatus(Long id, Integer status);

    void delete(Long id);
}
