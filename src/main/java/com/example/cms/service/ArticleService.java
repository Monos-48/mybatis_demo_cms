package com.example.cms.service;

import com.example.cms.common.PageVO;
import com.example.cms.dto.ArticleAuditDTO;
import com.example.cms.dto.ArticleCreateDTO;
import com.example.cms.dto.ArticleQueryDTO;
import com.example.cms.dto.ArticleUpdateDTO;
import com.example.cms.vo.ArticleDetailVO;
import com.example.cms.vo.ArticleRevisionVO;
import com.example.cms.vo.ArticleVO;
import com.example.cms.vo.PublicArticleDetailVO;
import com.example.cms.vo.PublicArticleVO;

import java.util.List;

/**
 * 文章内容业务，集中校验状态机与数据归属。
 */
public interface ArticleService {

    PageVO<ArticleVO> page(ArticleQueryDTO query);

    ArticleDetailVO detail(Long id);

    Long create(ArticleCreateDTO dto);

    void update(Long id, ArticleUpdateDTO dto);

    void delete(Long id);

    void submit(Long id);

    void audit(Long id, ArticleAuditDTO dto);

    void publish(Long id);

    void offline(Long id);

    void batchPublish(List<Long> articleIds);

    void batchOffline(List<Long> articleIds);

    /** 获取已发布文章的修订草稿；没有修订时返回 null。 */
    ArticleRevisionVO getRevision(Long articleId);

    /** 保存修订草稿；submitForReview=true 时直接进入待审核。 */
    void saveRevision(Long articleId, ArticleUpdateDTO dto);

    /** 提交修订审核。 */
    void submitRevision(Long articleId);

    /** 撤销尚未进入审核的修订。 */
    void discardRevision(Long articleId);

    /** 审核修订；通过后替换文章正文并重新发布。 */
    void auditRevision(Long articleId, ArticleAuditDTO dto);

    /** 读者端：仅返回已发布文章，且分类自身与祖先均启用。 */
    PageVO<PublicArticleVO> publicPage(ArticleQueryDTO query);

    /** 读者端详情：只读已发布文章，并累加阅读量。 */
    PublicArticleDetailVO publicDetail(Long id);
}
