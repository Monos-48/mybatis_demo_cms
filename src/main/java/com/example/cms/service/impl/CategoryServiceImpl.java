package com.example.cms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.cms.common.EnableStatus;
import com.example.cms.dto.CategorySaveDTO;
import com.example.cms.entity.CmsArticle;
import com.example.cms.entity.CmsCategory;
import com.example.cms.exception.BusinessException;
import com.example.cms.exception.NotFoundException;
import com.example.cms.mapper.CmsArticleMapper;
import com.example.cms.mapper.CmsCategoryMapper;
import com.example.cms.service.CategoryService;
import com.example.cms.vo.CategoryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类树业务实现：维护父子关系、同级重名校验、删除悬挂引用校验。
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CmsCategoryMapper categoryMapper;
    private final CmsArticleMapper articleMapper;

    public CategoryServiceImpl(CmsCategoryMapper categoryMapper, CmsArticleMapper articleMapper) {
        this.categoryMapper = categoryMapper;
        this.articleMapper = articleMapper;
    }

    @Override
    public List<CategoryVO> tree(boolean enabledOnly) {
        List<CmsCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<CmsCategory>()
                .orderByAsc(CmsCategory::getSort)
                .orderByAsc(CmsCategory::getId));
        Map<Long, CategoryVO> map = new LinkedHashMap<>();
        for (CmsCategory category : categories) {
            if (enabledOnly && (category.getStatus() == null
                    || category.getStatus() != EnableStatus.ENABLED.getCode())) {
                continue;
            }
            map.put(category.getId(), toVO(category));
        }

        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO node : map.values()) {
            CategoryVO parent = map.get(node.getParentId());
            if (node.getParentId() == null || node.getParentId() == 0L || parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public CategoryVO getDetail(Long id) {
        return toVO(requireCategory(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategorySaveDTO dto) {
        Long parentId = normalizeParentId(dto.getParentId());
        validateParent(parentId, null);
        ensureNameUnique(parentId, dto.getName().trim(), null);

        CmsCategory category = new CmsCategory();
        category.setName(dto.getName().trim());
        category.setParentId(parentId);
        category.setSort(dto.getSort() == null ? 0 : dto.getSort());
        category.setStatus(dto.getStatus() == null ? EnableStatus.ENABLED.getCode() : dto.getStatus());
        category.setDescription(dto.getDescription());
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategorySaveDTO dto) {
        CmsCategory category = requireCategory(id);
        Long parentId = normalizeParentId(dto.getParentId());
        validateParent(parentId, id);
        ensureNameUnique(parentId, dto.getName().trim(), id);

        category.setName(dto.getName().trim());
        category.setParentId(parentId);
        category.setSort(dto.getSort() == null ? category.getSort() : dto.getSort());
        category.setStatus(dto.getStatus() == null ? category.getStatus() : dto.getStatus());
        category.setDescription(dto.getDescription());
        categoryMapper.updateById(category);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        requireCategory(id);
        CmsCategory update = new CmsCategory();
        update.setId(id);
        update.setStatus(status);
        categoryMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireCategory(id);
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该分类下存在子分类，无法删除");
        }
        Long articleCount = articleMapper.selectCount(new LambdaQueryWrapper<CmsArticle>()
                .eq(CmsArticle::getCategoryId, id));
        if (articleCount != null && articleCount > 0) {
            throw new BusinessException("该分类下存在文章，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    private void validateParent(Long parentId, Long currentId) {
        if (parentId == 0L) {
            return;
        }
        if (currentId != null && parentId.equals(currentId)) {
            throw new BusinessException("父分类不能是自己");
        }
        CmsCategory parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException("父分类不存在");
        }
        if (currentId == null) {
            return;
        }
        // 防止将分类移动到自己的子孙节点下，形成环
        Long cursor = parentId;
        int depth = 0;
        while (cursor != null && cursor != 0L && depth++ < 100) {
            if (cursor.equals(currentId)) {
                throw new BusinessException("不能将分类移动到其子孙分类下");
            }
            CmsCategory node = categoryMapper.selectById(cursor);
            if (node == null) {
                break;
            }
            cursor = node.getParentId();
        }
    }

    private void ensureNameUnique(Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<CmsCategory> wrapper = new LambdaQueryWrapper<CmsCategory>()
                .eq(CmsCategory::getParentId, parentId)
                .eq(CmsCategory::getName, name);
        if (excludeId != null) {
            wrapper.ne(CmsCategory::getId, excludeId);
        }
        Long count = categoryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(409, "同级分类下已存在同名分类");
        }
    }

    private CmsCategory requireCategory(Long id) {
        CmsCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new NotFoundException("分类不存在");
        }
        return category;
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null || parentId < 0 ? 0L : parentId;
    }

    private CategoryVO toVO(CmsCategory category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setParentId(category.getParentId());
        vo.setSort(category.getSort());
        vo.setStatus(category.getStatus());
        vo.setStatusText(category.getStatus() != null
                && category.getStatus() == EnableStatus.ENABLED.getCode() ? "启用" : "停用");
        vo.setDescription(category.getDescription());
        vo.setCreateTime(category.getCreateTime());
        return vo;
    }
}
