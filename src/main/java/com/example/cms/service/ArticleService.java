package com.example.cms.service;

import com.example.cms.common.PageVO;
import com.example.cms.dto.ArticleAuditDTO;
import com.example.cms.dto.ArticleCreateDTO;
import com.example.cms.dto.ArticleQueryDTO;
import com.example.cms.dto.ArticleUpdateDTO;
import com.example.cms.vo.ArticleDetailVO;
import com.example.cms.vo.ArticleVO;

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
}
