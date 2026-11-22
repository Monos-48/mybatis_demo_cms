package com.example.cms.controller;

import com.example.cms.annotation.Log;
import com.example.cms.annotation.RequirePermission;
import com.example.cms.common.PageVO;
import com.example.cms.common.ResultVO;
import com.example.cms.dto.ArticleAuditDTO;
import com.example.cms.dto.ArticleCreateDTO;
import com.example.cms.dto.ArticleQueryDTO;
import com.example.cms.dto.ArticleUpdateDTO;
import com.example.cms.dto.BatchArticleDTO;
import com.example.cms.service.ArticleService;
import com.example.cms.vo.ArticleDetailVO;
import com.example.cms.vo.ArticleRevisionVO;
import com.example.cms.vo.ArticleVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 文章内容接口：CRUD、高级检索、审核状态流转、批量发布/下架。
 */
@RestController
@RequestMapping("/api/articles")
@Validated
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    @GetMapping
    @RequirePermission("article:list")
    public ResultVO<PageVO<ArticleVO>> page(@Valid ArticleQueryDTO query) {
        return ResultVO.success(articleService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("article:list")
    public ResultVO<ArticleDetailVO> detail(@PathVariable Long id) {
        return ResultVO.success(articleService.detail(id));
    }

    @PostMapping
    @RequirePermission("article:create")
    @Log(module = "文章管理", operation = "新增文章")
    public ResultVO<Long> create(@Valid @RequestBody ArticleCreateDTO dto) {
        return ResultVO.success("保存成功", articleService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePermission("article:edit")
    @Log(module = "文章管理", operation = "编辑文章")
    public ResultVO<Void> update(@PathVariable Long id,
                                 @Valid @RequestBody ArticleUpdateDTO dto) {
        articleService.update(id, dto);
        return ResultVO.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("article:delete")
    @Log(module = "文章管理", operation = "删除文章")
    public ResultVO<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return ResultVO.success();
    }

    @PostMapping("/{id}/submit")
    @RequirePermission("article:submit")
    @Log(module = "文章管理", operation = "提交审核")
    public ResultVO<Void> submit(@PathVariable Long id) {
        articleService.submit(id);
        return ResultVO.success();
    }

    @PostMapping("/{id}/audit")
    @RequirePermission("article:audit")
    @Log(module = "文章管理", operation = "审核文章")
    public ResultVO<Void> audit(@PathVariable Long id,
                                @Valid @RequestBody ArticleAuditDTO dto) {
        articleService.audit(id, dto);
        return ResultVO.success();
    }

    @PostMapping("/{id}/publish")
    @RequirePermission("article:publish")
    @Log(module = "文章管理", operation = "发布文章")
    public ResultVO<Void> publish(@PathVariable Long id) {
        articleService.publish(id);
        return ResultVO.success();
    }

    @PostMapping("/{id}/offline")
    @RequirePermission("article:offline")
    @Log(module = "文章管理", operation = "下架文章")
    public ResultVO<Void> offline(@PathVariable Long id) {
        articleService.offline(id);
        return ResultVO.success();
    }

    @PostMapping("/batch/publish")
    @RequirePermission("article:publish")
    public ResultVO<Void> batchPublish(@Valid @RequestBody BatchArticleDTO dto) {
        articleService.batchPublish(dto.getArticleIds());
        return ResultVO.success("批量发布成功", null);
    }

    @PostMapping("/batch/offline")
    @RequirePermission("article:offline")
    public ResultVO<Void> batchOffline(@Valid @RequestBody BatchArticleDTO dto) {
        articleService.batchOffline(dto.getArticleIds());
        return ResultVO.success("批量下架成功", null);
    }

    @GetMapping("/{id}/revision")
    @RequirePermission("article:list")
    public ResultVO<ArticleRevisionVO> revision(@PathVariable Long id) {
        return ResultVO.success(articleService.getRevision(id));
    }

    @PutMapping("/{id}/revision")
    @RequirePermission("article:edit")
    @Log(module = "文章管理", operation = "保存修订")
    public ResultVO<Void> saveRevision(@PathVariable Long id,
                                       @Valid @RequestBody ArticleUpdateDTO dto) {
        articleService.saveRevision(id, dto);
        return ResultVO.success("修订已保存", null);
    }

    @DeleteMapping("/{id}/revision")
    @RequirePermission("article:edit")
    @Log(module = "文章管理", operation = "撤销修订")
    public ResultVO<Void> discardRevision(@PathVariable Long id) {
        articleService.discardRevision(id);
        return ResultVO.success("修订已撤销", null);
    }

    @PostMapping("/{id}/revision/submit")
    @RequirePermission("article:submit")
    @Log(module = "文章管理", operation = "提交修订审核")
    public ResultVO<Void> submitRevision(@PathVariable Long id) {
        articleService.submitRevision(id);
        return ResultVO.success("修订已提交审核", null);
    }

    @PostMapping("/{id}/revision/audit")
    @RequirePermission("article:audit")
    @Log(module = "文章管理", operation = "审核修订")
    public ResultVO<Void> auditRevision(@PathVariable Long id,
                                        @Valid @RequestBody ArticleAuditDTO dto) {
        articleService.auditRevision(id, dto);
        return ResultVO.success("修订审核完成", null);
    }
}
