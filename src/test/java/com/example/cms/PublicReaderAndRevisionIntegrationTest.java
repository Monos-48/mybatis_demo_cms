package com.example.cms;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 读者端公开接口、阅读量、分类子树停用与已发布文章修订流程。
 */
class PublicReaderAndRevisionIntegrationTest extends BaseIntegrationTest {

    @Test
    void publicReaderOnlyReturnsPublishedContentAndCountsViews() throws Exception {
        MvcResult listResult = mockMvc.perform(get("/api/public/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(2))
                .andReturn();
        JsonNode record = responseBody(listResult).path("data").path("records").get(0);
        if (record.has("status") || record.has("authorId") || record.has("version")) {
            throw new AssertionError("公开列表暴露了内部字段：" + record);
        }

        // 非已发布文章对读者不可见
        mockMvc.perform(get("/api/public/articles/1")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/articles/3")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/articles/4")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/articles/6")).andExpect(status().isNotFound());

        // 公开详情访问累加阅读量（种子数据 356）
        mockMvc.perform(get("/api/public/articles/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(357));
        mockMvc.perform(get("/api/public/articles/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(358));

        // 管理端详情不累加
        MockHttpSession admin = loginAdmin();
        mockMvc.perform(get("/api/articles/2").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(358));

        // VIEWER 仍是内部观察员，可以看到草稿
        MockHttpSession viewer = login("viewer", "viewer123");
        MvcResult viewerList = mockMvc.perform(get("/api/articles")
                        .session(viewer)
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        boolean seesDraft = false;
        for (JsonNode item : responseBody(viewerList).path("data").path("records")) {
            if (item.path("id").asLong() == 5) {
                seesDraft = true;
            }
        }
        if (!seesDraft) {
            throw new AssertionError("VIEWER 作为内部观察员应能看到草稿");
        }
    }

    @Test
    void disablingParentCategoryHidesWholeSubtree() throws Exception {
        MockHttpSession admin = loginAdmin();
        MockHttpSession author = login("author", "author123");

        // 停用父分类 1（技术资讯），其子分类 3/4/5 应整体不可用
        mockMvc.perform(put("/api/categories/1/status")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("产品动态"))
                .andExpect(jsonPath("$.data[0].children", hasSize(1)));

        mockMvc.perform(get("/api/public/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 子分类自身仍是启用状态，但有效状态为停用
        mockMvc.perform(get("/api/categories/4").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.effectiveStatus").value(0));

        Map<String, Object> body = articleBody("父分类停用后新建文章",
                "这是一段用于验证父分类停用语义的正文内容。", 4, false);
        mockMvc.perform(post("/api/articles")
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("停用")));

        // 恢复父分类后，整棵子树重新可用
        mockMvc.perform(put("/api/categories/1/status")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void revisionKeepsPublishedContentUntilApproved() throws Exception {
        MockHttpSession author = login("author", "author123");
        MockHttpSession editor = login("editor", "editor123");
        long id = createAndPublish(author, editor, "修订流程原始标题",
                "原始正文内容，长度足够用于发布和阅读测试。");

        JsonNode published = publicDetail(id);
        if (!published.path("content").asText().contains("原始正文")) {
            throw new AssertionError("初始发布内容不正确");
        }

        // 作者保存修订并直接提交审核
        mockMvc.perform(put("/api/articles/{id}/revision", id)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(revisionBody("修订流程新标题",
                                "修订后的正文内容，包含新的业务说明。", 0, true))))
                .andExpect(status().isOk());

        // 审核前线上内容保持旧版本
        JsonNode stillOld = publicDetail(id);
        if (!stillOld.path("content").asText().contains("原始正文")) {
            throw new AssertionError("修订未审核前不应替换线上内容");
        }

        // 管理端列表带出待审核修订状态
        MvcResult list = mockMvc.perform(get("/api/articles")
                        .session(editor)
                        .param("title", "修订流程")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = responseBody(list).path("data").path("records");
        if (records.size() == 0 || records.get(0).path("revisionStatus").asInt() != 1) {
            throw new AssertionError("文章列表未返回待审核修订状态：" + records);
        }

        // 审核通过后替换线上内容
        mockMvc.perform(post("/api/articles/{id}/revision/audit", id)
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"remark\":\"修订通过\"}"))
                .andExpect(status().isOk());
        JsonNode updated = publicDetail(id);
        if (!"修订流程新标题".equals(updated.path("title").asText())
                || !updated.path("content").asText().contains("修订后的正文")) {
            throw new AssertionError("审核通过后线上内容未替换");
        }

        // 第二轮：修订被驳回，线上内容保持不变
        MvcResult revisionResult = mockMvc.perform(get("/api/articles/{id}/revision", id).session(author))
                .andExpect(status().isOk())
                .andReturn();
        int revisionVersion = responseBody(revisionResult).path("data").path("version").asInt();

        mockMvc.perform(put("/api/articles/{id}/revision", id)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(revisionBody("驳回测试标题",
                                "驳回测试正文，长度足够用于验证审核流程。", revisionVersion, true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/articles/{id}/revision/audit", id)
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"remark\":\"请补充数据来源\"}"))
                .andExpect(status().isOk());

        JsonNode afterReject = publicDetail(id);
        if (!afterReject.path("content").asText().contains("修订后的正文")) {
            throw new AssertionError("修订被驳回后不应替换线上内容");
        }
        mockMvc.perform(get("/api/articles/{id}/revision", id).session(author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(3))
                .andExpect(jsonPath("$.data.auditRemark").value("请补充数据来源"));
    }

    @Test
    void draftRevisionCanBeDiscarded() throws Exception {
        MockHttpSession author = login("author", "author123");
        MockHttpSession editor = login("editor", "editor123");
        long id = createAndPublish(author, editor, "修订草稿撤销测试",
                "用于验证修订草稿撤销的原始正文，长度足够。");

        mockMvc.perform(put("/api/articles/{id}/revision", id)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(revisionBody("修订草稿标题",
                                "这是一段尚未提交审核的修订正文，长度足够。", 0, false))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/{id}/revision", id).session(author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(delete("/api/articles/{id}/revision", id).session(author))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/articles/{id}/revision", id).session(author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private long createAndPublish(MockHttpSession author, MockHttpSession editor,
                                  String title, String content) throws Exception {
        long id = dataId(mockMvc.perform(post("/api/articles")
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(articleBody(title, content, 4, true))))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/articles/{id}/audit", id)
                        .session(editor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"remark\":\"初始审核通过\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/articles/{id}/publish", id).session(editor))
                .andExpect(status().isOk());
        return id;
    }

    private JsonNode publicDetail(long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/public/articles/{id}", id))
                .andExpect(status().isOk())
                .andReturn();
        return responseBody(result).path("data");
    }

    private Map<String, Object> articleBody(String title, String content,
                                            long categoryId, boolean submitForReview) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("summary", "集成测试摘要");
        body.put("content", content);
        body.put("categoryId", categoryId);
        body.put("submitForReview", submitForReview);
        return body;
    }

    private Map<String, Object> revisionBody(String title, String content,
                                             int version, boolean submitForReview) {
        Map<String, Object> body = articleBody(title, content, 4, submitForReview);
        body.put("version", version);
        return body;
    }
}
