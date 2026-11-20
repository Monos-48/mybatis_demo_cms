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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分类树、用户管理、角色权限与操作日志测试。
 */
class CategoryUserRoleLogIntegrationTest extends BaseIntegrationTest {

    @Test
    void categoryTreeSupportsEnabledFilter() throws Exception {
        MockHttpSession viewer = login("viewer", "viewer123");

        mockMvc.perform(get("/api/categories/tree").session(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].children", hasSize(3)))
                .andExpect(jsonPath("$.data[0].children[0].name").value("前端开发"));

        mockMvc.perform(get("/api/categories/tree")
                        .session(viewer)
                        .param("enabledOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/categories/999999").session(viewer))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoryCrudGuardsSiblingNamesCyclesAndReferences() throws Exception {
        MockHttpSession admin = loginAdmin();

        long parentId = createCategory(admin, "集成测试栏目", 0);
        long childId = createCategory(admin, "集成测试子栏目", parentId);

        mockMvc.perform(post("/api/categories")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("集成测试子栏目", parentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("同名")));

        mockMvc.perform(put("/api/categories/{id}", parentId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("集成测试栏目", childId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("子孙")));

        mockMvc.perform(delete("/api/categories/{id}", parentId).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("子分类")));

        mockMvc.perform(delete("/api/categories/7").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("文章")));

        mockMvc.perform(delete("/api/categories/5").session(admin))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/categories/4/status")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("状态值")));
    }

    @Test
    void userManagementAssemblesRolesAndProtectsCurrentAccount() throws Exception {
        MockHttpSession admin = loginAdmin();

        mockMvc.perform(get("/api/users")
                        .session(admin)
                        .param("keyword", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].roles", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].roleIds", hasItem(1)));

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", "qa_user_001");
        user.put("password", "qa123456");
        user.put("nickname", "质量保障用户");
        user.put("email", "qa@example.com");
        user.put("status", 1);
        user.put("roleIds", new long[]{3});
        MvcResult created = mockMvc.perform(post("/api/users")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].roleCode").value("AUTHOR"))
                .andReturn();
        long userId = createdId(created);

        MockHttpSession qa = login("qa_user_001", "qa123456");
        mockMvc.perform(get("/api/auth/me").session(qa))
                .andExpect(jsonPath("$.data.roles", hasItem("AUTHOR")));

        mockMvc.perform(put("/api/users/{id}/roles", userId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[4]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/{id}", userId).session(admin))
                .andExpect(jsonPath("$.data.roleIds", hasItem(4)))
                .andExpect(jsonPath("$.data.roles[0].roleCode").value("VIEWER"));

        mockMvc.perform(put("/api/users/{id}/password", userId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isOk());
        login("qa_user_001", "newpass123");

        mockMvc.perform(put("/api/users/{id}/status", userId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("qa_user_001", "newpass123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("禁用")));

        mockMvc.perform(put("/api/users/1/status")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("当前登录账号")));
        mockMvc.perform(put("/api/users/1/roles")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[2]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/users/1").session(admin))
                .andExpect(status().isBadRequest());

        Map<String, Object> badRole = new LinkedHashMap<>(user);
        badRole.put("username", "qa_user_002");
        badRole.put("roleIds", new long[]{9999});
        mockMvc.perform(post("/api/users")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(badRole)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("角色")));
    }

    @Test
    void roleAndPermissionManagement() throws Exception {
        MockHttpSession admin = loginAdmin();

        mockMvc.perform(get("/api/roles").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[1].permissionIds", hasSize(10)));
        mockMvc.perform(get("/api/permissions").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(13)));

        Map<String, Object> role = new LinkedHashMap<>();
        role.put("roleName", "测试审核员");
        role.put("roleCode", "TEST_REVIEWER");
        role.put("status", 1);
        role.put("remark", "集成测试临时角色");
        MvcResult created = mockMvc.perform(post("/api/roles")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(role)))
                .andExpect(status().isOk())
                .andReturn();
        long roleId = createdId(created);

        mockMvc.perform(post("/api/roles")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(role)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("编码已存在")));

        mockMvc.perform(put("/api/roles/{id}/permissions", roleId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[1,2]}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/roles/{id}", roleId).session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/roles/1").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("管理员角色")));
    }

    @Test
    void operationLogsAreFilterableAndMaskPasswords() throws Exception {
        MockHttpSession admin = loginAdmin();

        MvcResult result = mockMvc.perform(get("/api/logs")
                        .session(admin)
                        .param("module", "认证管理")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)))
                .andReturn();
        JsonNode records = responseBody(result).path("data").path("records");
        boolean masked = false;
        for (JsonNode record : records) {
            String params = record.path("params").asText();
            if (!params.contains("admin123") && params.contains("******")) {
                masked = true;
            }
            if (params.contains("admin123")) {
                throw new AssertionError("操作日志泄露了明文密码：" + params);
            }
        }
        if (!masked) {
            throw new AssertionError("操作日志未发现密码脱敏结果");
        }

        mockMvc.perform(get("/api/logs")
                        .session(admin)
                        .param("success", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/logs")
                        .session(admin)
                        .param("username", "admin")
                        .param("success", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value("admin"));
    }

    @Test
    void operationLogMasksPasswordsContainingQuotes() throws Exception {
        MockHttpSession admin = loginAdmin();
        String username = "reg_quote_001";
        String trickyPassword = "Abc12345\"SecretTail";

        Map<String, Object> register = new LinkedHashMap<>();
        register.put("username", username);
        register.put("password", trickyPassword);
        register.put("nickname", "引号密码测试");
        register.put("email", "quote@example.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register)))
                .andExpect(status().isOk());

        // 注册用户处于待审批状态，登录会被拒绝；失败请求同样写日志，参数也要完整脱敏
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, trickyPassword)))
                .andExpect(status().isForbidden());

        MvcResult result = mockMvc.perform(get("/api/logs")
                        .session(admin)
                        .param("module", "认证管理")
                        .param("pageSize", "100"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = responseBody(result).path("data").path("records");

        int matched = 0;
        for (JsonNode record : records) {
            String params = record.path("params").asText();
            if (!params.contains(username)) {
                continue;
            }
            matched++;
            if (params.contains("SecretTail")) {
                throw new AssertionError("操作日志泄露了引号后的密码片段：" + params);
            }
            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(params);
            } catch (Exception ex) {
                throw new AssertionError("脱敏后的参数不是合法 JSON：" + params, ex);
            }
            if (!parsed.isArray() || parsed.size() == 0
                    || !"******".equals(parsed.get(0).path("password").asText())) {
                throw new AssertionError("密码字段未被正确脱敏：" + params);
            }
        }
        if (matched < 2) {
            throw new AssertionError("未找到注册与登录两条脱敏日志，matched=" + matched);
        }
    }

    private long createCategory(MockHttpSession session, String name, long parentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(name, parentId)))
                .andExpect(status().isOk())
                .andReturn();
        return dataId(result);
    }

    private String categoryJson(String name, long parentId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("parentId", parentId);
        body.put("sort", 10);
        body.put("status", 1);
        body.put("description", "集成测试分类");
        return json(body);
    }
}
