package com.example.cms;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文章状态机、乐观锁、归属校验、批量操作与高级检索测试。
 */
class ArticleWorkflowIntegrationTest extends BaseIntegrationTest {

    @Test
    void fullWorkflowAndAuditMetadata() throws Exception {
        MockHttpSession author = login("author", "author123");
        MockHttpSession editor = login("editor", "editor123");

        long id = createArticle(author, "状态机集成测试文章", 4, false);

        mockMvc.perform(get("/api/articles/{id}", id).session(author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.authorId").value(3))
                .andExpect(jsonPath("$.data.version").value(0));

        mockMvc.perform(put("/api/articles/{id}", id)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("状态机集成测试文章（修订）", 4, 0, false)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/articles/{id}", id).session(author))
                .andExpect(jsonPath("$.data.title").value("状态机集成测试文章（修订）"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(post("/api/articles/{id}/submit", id).session(author))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/{id}", id).session(author))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.version").value(2));

        // 进入待审核后作者不可再编辑
        mockMvc.perform(put("/api/articles/{id}", id)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("待审核阶段修改", 4, 2, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("草稿或已驳回")));

        // 作者无审核权限，被 AOP 接口权限拦截
        mockMvc.perform(post("/api/articles/{id}/audit", id)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/articles/{id}", id).session(author))
                .andExpect(jsonPath("$.data.status").value(1));

        mockMvc.perform(post("/api/articles/{id}/audit", id)
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"remark\":\"结构完整，准予发布\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/{id}", id).session(editor))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.auditBy").value(2))
                .andExpect(jsonPath("$.data.auditRemark").value("结构完整，准予发布"));

        mockMvc.perform(post("/api/articles/{id}/publish", id).session(editor))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/{id}", id).session(editor))
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.publishTime").isNotEmpty());

        mockMvc.perform(post("/api/articles/{id}/offline", id).session(editor))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/{id}", id).session(editor))
                .andExpect(jsonPath("$.data.status").value(5));

        mockMvc.perform(post("/api/articles/{id}/publish", id).session(editor))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/articles/{id}/audit", id)
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("待审核")));
    }

    @Test
    void rejectedAuditRequiresRemark() throws Exception {
        MockHttpSession editor = login("editor", "editor123");
        mockMvc.perform(post("/api/articles/3/audit")
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("审核意见")));

        mockMvc.perform(post("/api/articles/3/audit")
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"remark\":\"内容需补充数据来源\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/3").session(editor))
                .andExpect(jsonPath("$.data.status").value(3))
                .andExpect(jsonPath("$.data.auditRemark").value("内容需补充数据来源"));
    }

    @Test
    void optimisticLockRejectsStaleVersion() throws Exception {
        MockHttpSession author = login("author", "author123");
        long id = createArticle(author, "乐观锁冲突测试文章", 4, false);

        mockMvc.perform(put("/api/articles/{id}", id)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("基于过期版本修改", 4, 99, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("刷新后重试")));

        mockMvc.perform(get("/api/articles/{id}", id).session(author))
                .andExpect(jsonPath("$.data.title").value("乐观锁冲突测试文章"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void validationAndBoundaryInput() throws Exception {
        MockHttpSession author = login("author", "author123");

        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("title", "x");
        invalid.put("content", "short");
        invalid.put("categoryId", 4);
        mockMvc.perform(post("/api/articles")
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("标题")))
                .andExpect(jsonPath("$.message").value(containsString("正文")));

        mockMvc.perform(post("/api/articles")
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(articleBody("停用分类不可写入", 7, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("停用")));

        mockMvc.perform(post("/api/articles")
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(get("/api/articles")
                        .session(author)
                        .param("sortField", "id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("排序字段")));

        mockMvc.perform(get("/api/articles/999999").session(author))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void sqlInjectionPayloadIsTreatedAsPlainText() throws Exception {
        MockHttpSession author = login("author", "author123");
        String injection = "SQL 注入验证' OR '1'='1";
        long id = createArticle(author, injection, 4, false);

        mockMvc.perform(get("/api/articles")
                        .session(author)
                        .param("title", "SQL 注入验证' OR '1'='1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) id))
                .andExpect(jsonPath("$.data.records[0].title").value(injection));
    }

    @Test
    void ownershipAndDeletionRules() throws Exception {
        MockHttpSession author = login("author", "author123");
        MockHttpSession editor = login("editor", "editor123");

        mockMvc.perform(delete("/api/articles/2").session(author))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("自己")));

        mockMvc.perform(delete("/api/articles/3").session(author))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("草稿或已驳回")));

        mockMvc.perform(delete("/api/articles/2").session(editor))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/2").session(editor))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchPublishAndOffline() throws Exception {
        MockHttpSession editor = login("editor", "editor123");

        mockMvc.perform(post("/api/articles/batch/publish")
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[1,6]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/1").session(editor))
                .andExpect(jsonPath("$.data.status").value(4));
        mockMvc.perform(get("/api/articles/6").session(editor))
                .andExpect(jsonPath("$.data.status").value(4));

        mockMvc.perform(post("/api/articles/batch/offline")
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[1,6]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/1").session(editor))
                .andExpect(jsonPath("$.data.status").value(5));

        mockMvc.perform(post("/api/articles/batch/publish")
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[5]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("状态不允许发布")));

        mockMvc.perform(post("/api/articles/batch/publish")
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("至少一篇文章")));
    }

    @Test
    void advancedSearchSupportsSubtreeStatusDateAndSort() throws Exception {
        MockHttpSession viewer = login("viewer", "viewer123");

        mockMvc.perform(get("/api/articles")
                        .session(viewer)
                        .param("categoryId", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4));

        mockMvc.perform(get("/api/articles")
                        .session(viewer)
                        .param("categoryId", "1")
                        .param("includeChildren", "false")
                        .param("pageSize", "20"))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/articles")
                        .session(viewer)
                        .param("status", "4"))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/articles")
                        .session(viewer)
                        .param("startDate", "2026-11-07")
                        .param("endDate", "2026-11-07"))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/articles")
                        .session(viewer)
                        .param("sortField", "viewCount")
                        .param("sortOrder", "desc")
                        .param("pageSize", "20"))
                .andExpect(jsonPath("$.data.records[0].viewCount").value(356));

        MvcResult paged = mockMvc.perform(get("/api/articles")
                        .session(viewer)
                        .param("pageNum", "1")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.pages").value(greaterThanOrEqualTo(3)))
                .andReturn();
        JsonNode body = responseBody(paged);
        if (body.path("data").path("total").asLong() < 6) {
            throw new AssertionError("种子文章数量异常");
        }
    }

    @Test
    void reApprovalClearsPreviousRejectionRemark() throws Exception {
        MockHttpSession author = login("author", "author123");
        MockHttpSession editor = login("editor", "editor123");

        long id = createArticle(author, "驳回后重新审核文章", 4, true);

        mockMvc.perform(post("/api/articles/{id}/audit", id)
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"remark\":\"请补充数据来源\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/articles/{id}/submit", id).session(author))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/articles/{id}/audit", id)
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/articles/{id}", id).session(editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2))
                .andReturn();
        JsonNode remark = responseBody(result).path("data").path("auditRemark");
        if (!remark.isMissingNode() && !remark.isNull() && !remark.asText().isEmpty()) {
            throw new AssertionError("重新审核通过后仍残留上一轮驳回意见：" + remark.asText());
        }
    }

    private long createArticle(MockHttpSession session, String title, long categoryId,
                               boolean submitForReview) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/articles")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(articleBody(title, categoryId, submitForReview))))
                .andExpect(status().isOk())
                .andReturn();
        return dataId(result);
    }

    private Map<String, Object> articleBody(String title, long categoryId,
                                            boolean submitForReview) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("summary", "集成测试摘要");
        body.put("content", "这是一段用于集成测试的正文内容，长度超过最小限制，可正常保存。");
        body.put("categoryId", categoryId);
        body.put("submitForReview", submitForReview);
        return body;
    }

    private String updateJson(String title, long categoryId, int version,
                              boolean submitForReview) throws Exception {
        Map<String, Object> body = articleBody(title, categoryId, submitForReview);
        body.put("version", version);
        return json(body);
    }
}
