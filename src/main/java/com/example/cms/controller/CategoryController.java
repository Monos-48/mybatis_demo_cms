package com.example.cms.controller;

import com.example.cms.annotation.Log;
import com.example.cms.annotation.RequirePermission;
import com.example.cms.common.ResultVO;
import com.example.cms.dto.CategorySaveDTO;
import com.example.cms.dto.CategoryStatusDTO;
import com.example.cms.service.CategoryService;
import com.example.cms.vo.CategoryVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 内容分类接口。
 */
@RestController
@RequestMapping("/api/categories")
@Validated
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 分类树。enabledOnly=true 时仅返回启用分类，供文章编辑器选择。
     */
    @GetMapping("/tree")
    @RequirePermission("category:list")
    public ResultVO<List<CategoryVO>> tree(
            @RequestParam(defaultValue = "false") boolean enabledOnly) {
        return ResultVO.success(categoryService.tree(enabledOnly));
    }

    @GetMapping("/{id}")
    @RequirePermission("category:list")
    public ResultVO<CategoryVO> detail(@PathVariable Long id) {
        return ResultVO.success(categoryService.getDetail(id));
    }

    @PostMapping
    @RequirePermission("category:manage")
    @Log(module = "分类管理", operation = "新增分类")
    public ResultVO<Long> create(@Valid @RequestBody CategorySaveDTO dto) {
        return ResultVO.success("分类创建成功", categoryService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePermission("category:manage")
    @Log(module = "分类管理", operation = "编辑分类")
    public ResultVO<Void> update(@PathVariable Long id,
                                 @Valid @RequestBody CategorySaveDTO dto) {
        categoryService.update(id, dto);
        return ResultVO.success();
    }

    @PutMapping("/{id}/status")
    @RequirePermission("category:manage")
    @Log(module = "分类管理", operation = "变更分类状态")
    public ResultVO<Void> updateStatus(@PathVariable Long id,
                                       @Valid @RequestBody CategoryStatusDTO dto) {
        categoryService.updateStatus(id, dto.getStatus());
        return ResultVO.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("category:manage")
    @Log(module = "分类管理", operation = "删除分类")
    public ResultVO<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResultVO.success();
    }
}
