package com.example.cms;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 操作日志边界：长文本参数不得因截断后超长导致日志写入失败。
 */
class OperationLogBoundaryIntegrationTest extends BaseIntegrationTest {

    @Test
    void longArticleStillProducesOperationLogWithinColumnWidth() throws Exception {
        MockHttpSession author = login("author", "author123");
        MockHttpSession admin = loginAdmin();

        String title = "长正文日志完整性测试";
        String content = "长".repeat(3000);
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", title);
        article.put("summary", "长正文");
        article.put("content", content);
        article.put("categoryId", 4);
        mockMvc.perform(post("/api/articles")
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(article)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/logs")
                        .session(admin)
                        .param("module", "文章管理")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = responseBody(result).path("data").path("records");

        boolean found = false;
        for (JsonNode record : records) {
            String params = record.path("params").asText();
            if (!"新增文章".equals(record.path("operation").asText())
                    || !params.contains(title)) {
                continue;
            }
            found = true;
            if (params.length() > 2000) {
                throw new AssertionError("操作日志 params 超出列宽：" + params.length());
            }
            if (params.contains(content)) {
                throw new AssertionError("操作日志仍记录了完整正文");
            }
        }
        if (!found) {
            throw new AssertionError("长文章保存成功但缺少对应的新增文章操作日志");
        }
    }
}
