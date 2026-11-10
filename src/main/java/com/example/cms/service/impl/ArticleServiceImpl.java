package com.example.cms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cms.common.ArticleStatus;
import com.example.cms.common.EnableStatus;
import com.example.cms.common.LoginUser;
import com.example.cms.common.PageVO;
import com.example.cms.dto.ArticleAuditDTO;
import com.example.cms.dto.ArticleCreateDTO;
import com.example.cms.dto.ArticleQueryDTO;
import com.example.cms.dto.ArticleUpdateDTO;
import com.example.cms.entity.CmsArticle;
import com.example.cms.entity.CmsCategory;
import com.example.cms.exception.BusinessException;
import com.example.cms.exception.NotFoundException;
import com.example.cms.exception.PermissionDeniedException;
import com.example.cms.mapper.CmsArticleMapper;
import com.example.cms.mapper.CmsCategoryMapper;
import com.example.cms.service.ArticleService;
import com.example.cms.service.OperationLogService;
import com.example.cms.util.PageUtils;
import com.example.cms.util.SecurityUtils;
import com.example.cms.vo.ArticleDetailVO;
import com.example.cms.vo.ArticleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 文章业务实现。
 * <p>状态机集中在此校验：草稿 -> 待审核 -> 通过/驳回 -> 发布 -> 下架；
 * 作者只能修改/删除自己的草稿或驳回文章，编辑/管理员可管理全部内容。</p>
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    private final CmsArticleMapper articleMapper;
    private final CmsCategoryMapper categoryMapper;
    private final OperationLogService operationLogService;

    public ArticleServiceImpl(CmsArticleMapper articleMapper, CmsCategoryMapper categoryMapper,
                              OperationLogService operationLogService) {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.operationLogService = operationLogService;
    }

    @Override
    public PageVO<ArticleVO> page(ArticleQueryDTO query) {
        Page<ArticleVO> page = PageUtils.toPage(query);
        List<Long> categoryIds = resolveCategoryIds(query);
        IPage<ArticleVO> result = articleMapper.selectArticlePage(page, query, categoryIds);
        for (ArticleVO article : result.getRecords()) {
            article.setStatusText(ArticleStatus.descriptionOf(article.getStatus()));
        }
        return PageVO.of(result);
    }

    @Override
    public ArticleDetailVO detail(Long id) {
        ArticleDetailVO detail = articleMapper.selectDetailById(id);
        if (detail == null) {
            throw new NotFoundException("文章不存在");
        }
        detail.setStatusText(ArticleStatus.descriptionOf(detail.getStatus()));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ArticleCreateDTO dto) {
        requireEnabledCategory(dto.getCategoryId());
        LoginUser loginUser = SecurityUtils.requireLoginUser();

        CmsArticle article = new CmsArticle();
        article.setTitle(dto.getTitle().trim());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCategoryId(dto.getCategoryId());
        article.setAuthorId(loginUser.getId());
        article.setStatus(Boolean.TRUE.equals(dto.getSubmitForReview())
                ? ArticleStatus.PENDING.getCode() : ArticleStatus.DRAFT.getCode());
        article.setViewCount(0);
        article.setVersion(0);
        articleMapper.insert(article);
        return article.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ArticleUpdateDTO dto) {
        CmsArticle article = requireArticle(id);
        checkOwnerOrManager(article);
        ensureEditable(article);
        requireEnabledCategory(dto.getCategoryId());

        article.setTitle(dto.getTitle().trim());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCategoryId(dto.getCategoryId());
        article.setVersion(dto.getVersion());
        if (Boolean.TRUE.equals(dto.getSubmitForReview())) {
            article.setStatus(ArticleStatus.PENDING.getCode());
        }
        if (articleMapper.updateById(article) != 1) {
            throw new BusinessException(409, "文章已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CmsArticle article = requireArticle(id);
        LoginUser loginUser = SecurityUtils.requireLoginUser();
        boolean manager = loginUser.isManager();
        boolean ownEditable = article.getAuthorId() != null
                && article.getAuthorId().equals(loginUser.getId())
                && (isStatus(article, ArticleStatus.DRAFT) || isStatus(article, ArticleStatus.REJECTED));
        boolean owner = article.getAuthorId() != null
                && article.getAuthorId().equals(loginUser.getId());
        if (!manager && !owner) {
            throw new PermissionDeniedException("只能删除自己创作的文章");
        }
        if (!manager && !ownEditable) {
            throw new BusinessException(409, "作者只能删除草稿或已驳回状态的文章");
        }
        articleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        CmsArticle article = requireArticle(id);
        checkOwnerOrManager(article);
        if (!isStatus(article, ArticleStatus.DRAFT) && !isStatus(article, ArticleStatus.REJECTED)) {
            throw new BusinessException(409, "只有草稿或已驳回文章可以提交审核");
        }
        article.setStatus(ArticleStatus.PENDING.getCode());
        updateWithOptimisticLock(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, ArticleAuditDTO dto) {
        CmsArticle article = requireArticle(id);
        if (!isStatus(article, ArticleStatus.PENDING)) {
            throw new BusinessException(409, "只有待审核文章可以执行审核");
        }
        if (Boolean.FALSE.equals(dto.getApproved())
                && (dto.getRemark() == null || dto.getRemark().trim().isEmpty())) {
            throw new BusinessException("驳回时必须填写审核意见");
        }

        article.setStatus(Boolean.TRUE.equals(dto.getApproved())
                ? ArticleStatus.APPROVED.getCode() : ArticleStatus.REJECTED.getCode());
        article.setAuditBy(SecurityUtils.requireLoginUser().getId());
        article.setAuditTime(LocalDateTime.now());
        article.setAuditRemark(dto.getRemark());
        updateWithOptimisticLock(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        CmsArticle article = requireArticle(id);
        if (!isStatus(article, ArticleStatus.APPROVED) && !isStatus(article, ArticleStatus.OFFLINE)) {
            throw new BusinessException(409, "只有审核通过或已下架的文章可以发布");
        }
        article.setStatus(ArticleStatus.PUBLISHED.getCode());
        article.setPublishTime(LocalDateTime.now());
        updateWithOptimisticLock(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offline(Long id) {
        CmsArticle article = requireArticle(id);
        if (!isStatus(article, ArticleStatus.PUBLISHED)) {
            throw new BusinessException(409, "只有已发布文章可以下架");
        }
        article.setStatus(ArticleStatus.OFFLINE.getCode());
        updateWithOptimisticLock(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchPublish(List<Long> articleIds) {
        List<CmsArticle> articles = loadBatch(articleIds);
        for (CmsArticle article : articles) {
            if (!isStatus(article, ArticleStatus.APPROVED) && !isStatus(article, ArticleStatus.OFFLINE)) {
                throw new BusinessException(409,
                        "文章《" + article.getTitle() + "》状态不允许发布，请先审核通过");
            }
        }
        for (CmsArticle article : articles) {
            article.setStatus(ArticleStatus.PUBLISHED.getCode());
            article.setPublishTime(LocalDateTime.now());
            updateWithOptimisticLock(article);
            operationLogService.record("文章管理", "批量发布",
                    "文章ID=" + article.getId() + "，标题=" + article.getTitle());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchOffline(List<Long> articleIds) {
        List<CmsArticle> articles = loadBatch(articleIds);
        for (CmsArticle article : articles) {
            if (!isStatus(article, ArticleStatus.PUBLISHED)) {
                throw new BusinessException(409,
                        "文章《" + article.getTitle() + "》不是已发布状态，无法下架");
            }
        }
        for (CmsArticle article : articles) {
            article.setStatus(ArticleStatus.OFFLINE.getCode());
            updateWithOptimisticLock(article);
            operationLogService.record("文章管理", "批量下架",
                    "文章ID=" + article.getId() + "，标题=" + article.getTitle());
        }
    }

    /**
     * 计算分类过滤集合。includeChildren=true 时一次查询全部分类后在内存展开子树。
     */
    private List<Long> resolveCategoryIds(ArticleQueryDTO query) {
        if (query.getCategoryId() == null) {
            return Collections.emptyList();
        }
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(query.getCategoryId());
        if (!Boolean.FALSE.equals(query.getIncludeChildren())) {
            List<CmsCategory> categories = categoryMapper.selectList(null);
            boolean changed = true;
            while (changed) {
                changed = false;
                for (CmsCategory category : categories) {
                    if (category.getParentId() != null && ids.contains(category.getParentId())
                            && ids.add(category.getId())) {
                        changed = true;
                    }
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private List<CmsArticle> loadBatch(List<Long> articleIds) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(articleIds));
        ids.remove(null);
        if (ids.isEmpty()) {
            throw new BusinessException("请选择至少一篇文章");
        }
        List<CmsArticle> articles = articleMapper.selectBatchIds(ids);
        if (articles.size() != ids.size()) {
            throw new NotFoundException("部分文章不存在或已被删除");
        }
        return articles;
    }

    private void requireEnabledCategory(Long categoryId) {
        CmsCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
        if (category.getStatus() == null
                || category.getStatus() != EnableStatus.ENABLED.getCode()) {
            throw new BusinessException("分类已停用，不能挂载新文章");
        }
    }

    private CmsArticle requireArticle(Long id) {
        CmsArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new NotFoundException("文章不存在");
        }
        return article;
    }

    private void checkOwnerOrManager(CmsArticle article) {
        LoginUser loginUser = SecurityUtils.requireLoginUser();
        if (!loginUser.isManager()
                && (article.getAuthorId() == null || !article.getAuthorId().equals(loginUser.getId()))) {
            throw new PermissionDeniedException("只能操作自己创作的文章");
        }
    }

    private void ensureEditable(CmsArticle article) {
        if (!isStatus(article, ArticleStatus.DRAFT) && !isStatus(article, ArticleStatus.REJECTED)) {
            throw new BusinessException(409, "只有草稿或已驳回状态的文章可以编辑");
        }
    }

    private boolean isStatus(CmsArticle article, ArticleStatus status) {
        return article.getStatus() != null && article.getStatus() == status.getCode();
    }

    private void updateWithOptimisticLock(CmsArticle article) {
        if (articleMapper.updateById(article) != 1) {
            throw new BusinessException(409, "文章已被其他用户修改，请刷新后重试");
        }
    }
}
