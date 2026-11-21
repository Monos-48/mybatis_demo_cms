package com.example.cms;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文章删除并发回归测试。
 * <p>在 service 读取文章并完成校验之后、真正执行删除之前，注入一次“提交审核”，
 * 用于验证条件删除会因版本/状态变化而失败，而不是删掉状态已改变的文章。</p>
 */
@Import(ArticleDeleteConcurrencyIntegrationTest.DeleteInterleaveConfig.class)
class ArticleDeleteConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DeleteInterleaveAspect deleteInterleaveAspect;

    @Test
    void deleteRejectsWhenArticleStatusChangesBeforeDelete() throws Exception {
        MockHttpSession author = login("author", "author123");
        long id = createDraft(author);

        // 下一次 CmsArticleMapper.delete 执行前，先模拟另一个请求把文章提交审核
        deleteInterleaveAspect.interleaveFor(id);

        mockMvc.perform(delete("/api/articles/{id}", id).session(author))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("刷新")));
    }

    private long createDraft(MockHttpSession session) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "并发删除测试文章");
        body.put("summary", "集成测试摘要");
        body.put("content", "这是一段用于并发删除测试的正文内容，长度满足最小限制。");
        body.put("categoryId", 4);
        return dataId(mockMvc.perform(post("/api/articles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn());
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    static class DeleteInterleaveConfig {

        @Bean
        DeleteInterleaveAspect deleteInterleaveAspect(JdbcTemplate jdbcTemplate) {
            return new DeleteInterleaveAspect(jdbcTemplate);
        }
    }

    /**
     * 拦截文章 Mapper 的删除调用，在真正执行 SQL 前修改目标文章状态与版本。
     */
    @Aspect
    static class DeleteInterleaveAspect {

        private final JdbcTemplate jdbcTemplate;
        private volatile Long targetId;

        DeleteInterleaveAspect(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        void interleaveFor(Long id) {
            this.targetId = id;
        }

        @Around("execution(* com.example.cms.mapper.CmsArticleMapper.delete(..))")
        public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
            Long id = targetId;
            if (id != null) {
                targetId = null;
                jdbcTemplate.update(
                        "UPDATE cms_article SET status = 1, version = version + 1 WHERE id = ?", id);
            }
            return joinPoint.proceed();
        }
    }
}
