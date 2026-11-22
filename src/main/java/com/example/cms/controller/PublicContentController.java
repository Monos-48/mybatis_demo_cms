package com.example.cms.controller;

import com.example.cms.common.PageVO;
import com.example.cms.common.ResultVO;
import com.example.cms.dto.ArticleQueryDTO;
import com.example.cms.service.ArticleService;
import com.example.cms.service.CategoryService;
import com.example.cms.vo.CategoryVO;
import com.example.cms.vo.PublicArticleDetailVO;
import com.example.cms.vo.PublicArticleVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 读者端公开接口：无需登录，只暴露已发布且分类有效的文章。
 */
@RestController
@RequestMapping("/api/public")
@Validated
public class PublicContentController {

    private final ArticleService articleService;
    private final CategoryService categoryService;

    public PublicContentController(ArticleService articleService, CategoryService categoryService) {
        this.articleService = articleService;
        this.categoryService = categoryService;
    }

    @GetMapping("/articles")
    public ResultVO<PageVO<PublicArticleVO>> articles(@Valid ArticleQueryDTO query) {
        return ResultVO.success(articleService.publicPage(query));
    }

    @GetMapping("/articles/{id}")
    public ResultVO<PublicArticleDetailVO> article(@PathVariable Long id) {
        return ResultVO.success(articleService.publicDetail(id));
    }

    @GetMapping("/categories/tree")
    public ResultVO<List<CategoryVO>> categories() {
        return ResultVO.success(categoryService.tree(true));
    }
}
