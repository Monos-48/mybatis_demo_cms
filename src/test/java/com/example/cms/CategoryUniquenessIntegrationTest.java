package com.example.cms;

import com.example.cms.entity.CmsCategory;
import com.example.cms.mapper.CmsCategoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 数据库层同级分类唯一约束：并发创建时由唯一索引兜底，逻辑删除后允许重建。
 */
class CategoryUniquenessIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CmsCategoryMapper categoryMapper;

    @Test
    void databaseEnforcesUniqueSiblingCategoryNameAndAllowsReuseAfterDelete() {
        CmsCategory first = newCategory("唯一约束测试栏目", 0L);
        categoryMapper.insert(first);

        CmsCategory duplicate = newCategory("唯一约束测试栏目", 0L);
        assertThrows(DataIntegrityViolationException.class,
                () -> categoryMapper.insert(duplicate));

        // 逻辑删除后 active_name 变为 NULL，同名分类可以重新创建
        categoryMapper.deleteById(first.getId());
        CmsCategory reused = newCategory("唯一约束测试栏目", 0L);
        categoryMapper.insert(reused);

        CmsCategory sameParentDifferentName = newCategory("唯一约束测试栏目二", 0L);
        categoryMapper.insert(sameParentDifferentName);
    }

    private CmsCategory newCategory(String name, Long parentId) {
        CmsCategory category = new CmsCategory();
        category.setName(name);
        category.setParentId(parentId);
        category.setSort(0);
        category.setStatus(1);
        return category;
    }
}
