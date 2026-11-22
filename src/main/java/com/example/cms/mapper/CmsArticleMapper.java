package com.example.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cms.dto.ArticleQueryDTO;
import com.example.cms.entity.CmsArticle;
import com.example.cms.vo.ArticleDetailVO;
import com.example.cms.vo.ArticleVO;
import com.example.cms.vo.PublicArticleDetailVO;
import com.example.cms.vo.PublicArticleVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章持久层。列表和详情使用 JOIN 一次取回关联名称，避免 N+1 查询。
 */
public interface CmsArticleMapper extends BaseMapper<CmsArticle> {

    IPage<ArticleVO> selectArticlePage(Page<ArticleVO> page,
                                       @Param("q") ArticleQueryDTO query,
                                       @Param("categoryIds") List<Long> categoryIds);

    ArticleDetailVO selectDetailById(@Param("id") Long id);

    IPage<PublicArticleVO> selectPublicArticlePage(Page<PublicArticleVO> page,
                                                   @Param("q") ArticleQueryDTO query,
                                                   @Param("categoryIds") List<Long> categoryIds);

    PublicArticleDetailVO selectPublicDetailById(@Param("id") Long id,
                                                 @Param("categoryIds") List<Long> categoryIds);

    int incrementViewCount(@Param("id") Long id);
}
