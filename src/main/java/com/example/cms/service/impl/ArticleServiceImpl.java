package com.example.cms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cms.common.ArticleStatus;
import com.example.cms.common.LoginUser;
import com.example.cms.common.PageVO;
import com.example.cms.common.RevisionStatus;
import com.example.cms.dto.ArticleAuditDTO;
import com.example.cms.dto.ArticleCreateDTO;
import com.example.cms.dto.ArticleQueryDTO;
import com.example.cms.dto.ArticleUpdateDTO;
import com.example.cms.entity.CmsArticle;
import com.example.cms.entity.CmsArticleRevision;
import com.example.cms.entity.CmsCategory;
import com.example.cms.exception.BusinessException;
import com.example.cms.exception.NotFoundException;
import com.example.cms.exception.PermissionDeniedException;
import com.example.cms.mapper.CmsArticleMapper;
import com.example.cms.mapper.CmsArticleRevisionMapper;
import com.example.cms.mapper.CmsCategoryMapper;
import com.example.cms.service.ArticleService;
import com.example.cms.service.CategoryService;
import com.example.cms.service.OperationLogService;
import com.example.cms.util.PageUtils;
import com.example.cms.util.SecurityUtils;
import com.example.cms.vo.ArticleDetailVO;
import com.example.cms.vo.ArticleRevisionVO;
import com.example.cms.vo.ArticleVO;
import com.example.cms.vo.PublicArticleDetailVO;
import com.example.cms.vo.PublicArticleVO;
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
    private final CmsArticleRevisionMapper revisionMapper;
    private final CategoryService categoryService;
    private final OperationLogService operationLogService;

    public ArticleServiceImpl(CmsArticleMapper articleMapper, CmsCategoryMapper categoryMapper,
                              CmsArticleRevisionMapper revisionMapper,
                              CategoryService categoryService,
                              OperationLogService operationLogService) {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.revisionMapper = revisionMapper;
        this.categoryService = categoryService;
        this.operationLogService = operationLogService;
    }

    @Override
    public PageVO<ArticleVO> page(ArticleQueryDTO query) {
        Page<ArticleVO> page = PageUtils.toPage(query);
        List<Long> categoryIds = resolveCategoryIds(query);
        IPage<ArticleVO> result = articleMapper.selectArticlePage(page, query, categoryIds);
        for (ArticleVO article : result.getRecords()) {
            article.setStatusText(ArticleStatus.descriptionOf(article.getStatus()));
            article.setRevisionStatusText(RevisionStatus.descriptionOf(article.getRevisionStatus()));
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

        // 删除时再次约束版本与状态，避免“校验后、删除前”文章被并发提交/修改
        LambdaQueryWrapper<CmsArticle> wrapper = new LambdaQueryWrapper<CmsArticle>()
                .eq(CmsArticle::getId, id)
                .eq(CmsArticle::getVersion, article.getVersion());
        if (!manager) {
            wrapper.eq(CmsArticle::getAuthorId, loginUser.getId())
                    .in(CmsArticle::getStatus,
                            ArticleStatus.DRAFT.getCode(), ArticleStatus.REJECTED.getCode());
        }
        if (articleMapper.delete(wrapper) != 1) {
            throw new BusinessException(409, "文章状态已发生变化，请刷新后重试");
        }
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

        Integer newStatus = Boolean.TRUE.equals(dto.getApproved())
                ? ArticleStatus.APPROVED.getCode() : ArticleStatus.REJECTED.getCode();
        Long auditorId = SecurityUtils.requireLoginUser().getId();
        LocalDateTime now = LocalDateTime.now();

        // 使用 UpdateWrapper 显式写入 audit_remark：审核通过且未填写意见时会写入 NULL，
        // 避免 updateById 的默认 NOT_NULL 策略跳过空值、把上一轮驳回意见残留下来。
        int rows = articleMapper.update(null, new LambdaUpdateWrapper<CmsArticle>()
                .eq(CmsArticle::getId, article.getId())
                .eq(CmsArticle::getVersion, article.getVersion())
                .set(CmsArticle::getStatus, newStatus)
                .set(CmsArticle::getAuditBy, auditorId)
                .set(CmsArticle::getAuditTime, now)
                .set(CmsArticle::getAuditRemark, dto.getRemark())
                .set(CmsArticle::getUpdateTime, now)
                .set(CmsArticle::getUpdateBy, auditorId)
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new BusinessException(409, "文章已被其他用户修改，请刷新后重试");
        }
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

    @Override
    public PageVO<PublicArticleVO> publicPage(ArticleQueryDTO query) {
        List<Long> effective = new ArrayList<>(categoryService.effectiveEnabledCategoryIds());
        List<Long> requested = resolveCategoryIds(query);
        List<Long> categoryIds;
        if (requested.isEmpty()) {
            categoryIds = effective;
        } else {
            requested.retainAll(new HashSet<>(effective));
            categoryIds = requested;
        }
        Page<PublicArticleVO> page = PageUtils.toPage(query);
        if (categoryIds.isEmpty()) {
            page.setRecords(Collections.emptyList());
            page.setTotal(0);
            return PageVO.of(page);
        }
        IPage<PublicArticleVO> result = articleMapper.selectPublicArticlePage(page, query, categoryIds);
        return PageVO.of(result);
    }

    @Override
    public PublicArticleDetailVO publicDetail(Long id) {
        List<Long> effective = new ArrayList<>(categoryService.effectiveEnabledCategoryIds());
        if (effective.isEmpty()) {
            throw new NotFoundException("文章不存在");
        }
        PublicArticleDetailVO detail = articleMapper.selectPublicDetailById(id, effective);
        if (detail == null) {
            throw new NotFoundException("文章不存在");
        }
        // 只有公开详情访问才累加阅读量；管理端详情不计入
        articleMapper.incrementViewCount(id);
        detail.setViewCount((detail.getViewCount() == null ? 0 : detail.getViewCount()) + 1);
        return detail;
    }

    @Override
    public ArticleRevisionVO getRevision(Long articleId) {
        CmsArticle article = requireArticle(articleId);
        checkOwnerOrManager(article);
        CmsArticleRevision revision = findRevision(articleId);
        return revision == null ? null : toRevisionVO(revision);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRevision(Long articleId, ArticleUpdateDTO dto) {
        CmsArticle article = requireArticle(articleId);
        checkOwnerOrManager(article);
        if (!isStatus(article, ArticleStatus.PUBLISHED)
                && !isStatus(article, ArticleStatus.OFFLINE)) {
            throw new BusinessException(409, "只有已发布或已下架文章可以创建修订");
        }
        requireEnabledCategory(dto.getCategoryId());

        CmsArticleRevision revision = findRevision(articleId);
        if (revision != null && isRevisionStatus(revision, RevisionStatus.PENDING)) {
            throw new BusinessException(409, "修订正在审核中，不能修改");
        }

        Integer newStatus = Boolean.TRUE.equals(dto.getSubmitForReview())
                ? RevisionStatus.PENDING.getCode() : RevisionStatus.DRAFT.getCode();
        if (revision == null) {
            revision = new CmsArticleRevision();
            revision.setArticleId(articleId);
            revision.setTitle(dto.getTitle().trim());
            revision.setSummary(dto.getSummary());
            revision.setContent(dto.getContent());
            revision.setCategoryId(dto.getCategoryId());
            revision.setStatus(newStatus);
            revision.setVersion(0);
            revisionMapper.insert(revision);
            return;
        }

        int rows = revisionMapper.update(null, new LambdaUpdateWrapper<CmsArticleRevision>()
                .eq(CmsArticleRevision::getId, revision.getId())
                .eq(CmsArticleRevision::getVersion, dto.getVersion())
                .set(CmsArticleRevision::getTitle, dto.getTitle().trim())
                .set(CmsArticleRevision::getSummary, dto.getSummary())
                .set(CmsArticleRevision::getContent, dto.getContent())
                .set(CmsArticleRevision::getCategoryId, dto.getCategoryId())
                .set(CmsArticleRevision::getStatus, newStatus)
                .set(CmsArticleRevision::getAuditBy, null)
                .set(CmsArticleRevision::getAuditTime, null)
                .set(CmsArticleRevision::getAuditRemark, null)
                .set(CmsArticleRevision::getUpdateTime, LocalDateTime.now())
                .set(CmsArticleRevision::getUpdateBy, SecurityUtils.getCurrentUserId())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new BusinessException(409, "修订已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitRevision(Long articleId) {
        CmsArticle article = requireArticle(articleId);
        checkOwnerOrManager(article);
        CmsArticleRevision revision = requireRevision(articleId);
        if (!isRevisionStatus(revision, RevisionStatus.DRAFT)
                && !isRevisionStatus(revision, RevisionStatus.REJECTED)) {
            throw new BusinessException(409, "只有修订草稿或已驳回修订可以提交审核");
        }
        if (!categoryService.isEffectivelyEnabled(revision.getCategoryId())) {
            throw new BusinessException(409, "修订分类已停用，请调整分类后再提交");
        }

        int rows = revisionMapper.update(null, new LambdaUpdateWrapper<CmsArticleRevision>()
                .eq(CmsArticleRevision::getId, revision.getId())
                .eq(CmsArticleRevision::getVersion, revision.getVersion())
                .set(CmsArticleRevision::getStatus, RevisionStatus.PENDING.getCode())
                .set(CmsArticleRevision::getAuditBy, null)
                .set(CmsArticleRevision::getAuditTime, null)
                .set(CmsArticleRevision::getAuditRemark, null)
                .set(CmsArticleRevision::getUpdateTime, LocalDateTime.now())
                .set(CmsArticleRevision::getUpdateBy, SecurityUtils.getCurrentUserId())
                .setSql("version = version + 1"));
        if (rows != 1) {
            throw new BusinessException(409, "修订已被其他用户修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void discardRevision(Long articleId) {
        CmsArticle article = requireArticle(articleId);
        checkOwnerOrManager(article);
        CmsArticleRevision revision = requireRevision(articleId);
        if (isRevisionStatus(revision, RevisionStatus.PENDING)) {
            throw new BusinessException(409, "修订正在审核中，不能撤销");
        }
        revisionMapper.deleteById(revision.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditRevision(Long articleId, ArticleAuditDTO dto) {
        CmsArticle article = requireArticle(articleId);
        CmsArticleRevision revision = requireRevision(articleId);
        if (!isRevisionStatus(revision, RevisionStatus.PENDING)) {
            throw new BusinessException(409, "只有待审核修订可以执行审核");
        }
        if (Boolean.FALSE.equals(dto.getApproved())
                && (dto.getRemark() == null || dto.getRemark().trim().isEmpty())) {
            throw new BusinessException("驳回时必须填写审核意见");
        }

        Long auditorId = SecurityUtils.requireLoginUser().getId();
        LocalDateTime now = LocalDateTime.now();
        boolean approved = Boolean.TRUE.equals(dto.getApproved());

        if (approved) {
            if (!isStatus(article, ArticleStatus.PUBLISHED)
                    && !isStatus(article, ArticleStatus.OFFLINE)) {
                throw new BusinessException(409, "文章当前状态不能应用修订");
            }
            if (!categoryService.isEffectivelyEnabled(revision.getCategoryId())) {
                throw new BusinessException(409, "修订分类已停用，无法发布，请先调整分类");
            }
            // 修订通过后整体替换线上正文，并重新发布
            int articleRows = articleMapper.update(null, new LambdaUpdateWrapper<CmsArticle>()
                    .eq(CmsArticle::getId, article.getId())
                    .eq(CmsArticle::getVersion, article.getVersion())
                    .set(CmsArticle::getTitle, revision.getTitle())
                    .set(CmsArticle::getSummary, revision.getSummary())
                    .set(CmsArticle::getContent, revision.getContent())
                    .set(CmsArticle::getCategoryId, revision.getCategoryId())
                    .set(CmsArticle::getStatus, ArticleStatus.PUBLISHED.getCode())
                    .set(CmsArticle::getPublishTime, now)
                    .set(CmsArticle::getAuditBy, auditorId)
                    .set(CmsArticle::getAuditTime, now)
                    .set(CmsArticle::getAuditRemark, dto.getRemark())
                    .set(CmsArticle::getUpdateTime, now)
                    .set(CmsArticle::getUpdateBy, auditorId)
                    .setSql("version = version + 1"));
            if (articleRows != 1) {
                throw new BusinessException(409, "文章已被其他用户修改，请刷新后重试");
            }
        }

        Integer revisionStatus = approved
                ? RevisionStatus.APPROVED.getCode() : RevisionStatus.REJECTED.getCode();
        int revisionRows = revisionMapper.update(null, new LambdaUpdateWrapper<CmsArticleRevision>()
                .eq(CmsArticleRevision::getId, revision.getId())
                .eq(CmsArticleRevision::getVersion, revision.getVersion())
                .set(CmsArticleRevision::getStatus, revisionStatus)
                .set(CmsArticleRevision::getAuditBy, auditorId)
                .set(CmsArticleRevision::getAuditTime, now)
                .set(CmsArticleRevision::getAuditRemark, dto.getRemark())
                .set(CmsArticleRevision::getUpdateTime, now)
                .set(CmsArticleRevision::getUpdateBy, auditorId)
                .setSql("version = version + 1"));
        if (revisionRows != 1) {
            throw new BusinessException(409, "修订已被其他用户修改，请刷新后重试");
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
        if (!categoryService.isEffectivelyEnabled(categoryId)) {
            throw new BusinessException("分类不存在或已停用（含父分类停用），不能挂载新文章");
        }
    }

    private CmsArticleRevision findRevision(Long articleId) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<CmsArticleRevision>()
                .eq(CmsArticleRevision::getArticleId, articleId));
    }

    private CmsArticleRevision requireRevision(Long articleId) {
        CmsArticleRevision revision = findRevision(articleId);
        if (revision == null) {
            throw new NotFoundException("该文章没有修订草稿");
        }
        return revision;
    }

    private boolean isRevisionStatus(CmsArticleRevision revision, RevisionStatus status) {
        return revision.getStatus() != null && revision.getStatus() == status.getCode();
    }

    private ArticleRevisionVO toRevisionVO(CmsArticleRevision revision) {
        ArticleRevisionVO vo = new ArticleRevisionVO();
        vo.setId(revision.getId());
        vo.setArticleId(revision.getArticleId());
        vo.setTitle(revision.getTitle());
        vo.setSummary(revision.getSummary());
        vo.setContent(revision.getContent());
        vo.setCategoryId(revision.getCategoryId());
        vo.setStatus(revision.getStatus());
        vo.setStatusText(RevisionStatus.descriptionOf(revision.getStatus()));
        vo.setAuditRemark(revision.getAuditRemark());
        vo.setAuditTime(revision.getAuditTime());
        vo.setVersion(revision.getVersion());
        vo.setUpdateTime(revision.getUpdateTime());
        CmsCategory category = categoryMapper.selectById(revision.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        return vo;
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
