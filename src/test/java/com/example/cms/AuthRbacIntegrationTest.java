package com.example.cms;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证、会话与接口级 RBAC 权限拦截测试。
 */
class AuthRbacIntegrationTest extends BaseIntegrationTest {

    @Test
    void anonymousRequestIsRejectedWithUnifiedBody() throws Exception {
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value(containsString("登录")));
    }

    @Test
    void adminLoginReturnsRolesAndPermissions() throws Exception {
        MockHttpSession session = loginAdmin();
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.roles", hasItem("ADMIN")))
                .andExpect(jsonPath("$.data.permissions", hasSize(13)))
                .andExpect(jsonPath("$.data.permissions", hasItem("article:audit")))
                .andExpect(jsonPath("$.data.permissions", hasItem("user:manage")));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void pendingUserCannotLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("pending", "pending123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value(containsString("审批")));
    }

    @Test
    void authorCannotAccessAdminInterfaces() throws Exception {
        MockHttpSession author = login("author", "author123");

        mockMvc.perform(get("/api/users").session(author))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/logs").session(author))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(post("/api/articles/1/audit")
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"remark\":\"越权审核\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void viewerIsReadOnly() throws Exception {
        MockHttpSession viewer = login("viewer", "viewer123");
        mockMvc.perform(get("/api/articles").session(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> article = new LinkedHashMap<>();
        article.put("title", "只读用户不应创建成功");
        article.put("content", "这是一段长度足够但不应被只读用户写入的正文内容。");
        article.put("categoryId", 4);
        mockMvc.perform(post("/api/articles")
                        .session(viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(article)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void registeredUserIsPendingUntilApproved() throws Exception {
        Map<String, Object> register = new LinkedHashMap<>();
        register.put("username", "reg_user_001");
        register.put("password", "secret123");
        register.put("nickname", "注册测试用户");
        register.put("email", "reg@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.roles[0].roleCode").value("AUTHOR"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("reg_user_001", "secret123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("审批")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void registrationValidationRejectsWeakInput() throws Exception {
        Map<String, Object> register = new LinkedHashMap<>();
        register.put("username", "a!");
        register.put("password", "12345678");
        register.put("nickname", "x");
        register.put("email", "not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(register)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("用户名")))
                .andExpect(jsonPath("$.message").value(containsString("密码")))
                .andExpect(jsonPath("$.message").value(containsString("昵称")))
                .andExpect(jsonPath("$.message").value(containsString("邮箱")));
    }

    @Test
    void downgradedManagerLosesDataPermissionInExistingSession() throws Exception {
        MockHttpSession editor = login("editor", "editor123");
        MockHttpSession admin = loginAdmin();

        // 管理员把 editor（用户 2）从 EDITOR 降级为 AUTHOR（角色 3）
        mockMvc.perform(put("/api/users/2/roles")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[3]}"))
                .andExpect(status().isOk());

        // 不调用 /api/auth/me，直接复用旧会话操作 author（用户 3）的文章 1。
        // AUTHOR 仍拥有 article:delete / article:submit 接口权限，
        // 但业务层必须按数据库中的最新角色判定，不能再以 EDITOR 身份绕过归属校验。
        mockMvc.perform(delete("/api/articles/1").session(editor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("自己")));

        mockMvc.perform(post("/api/articles/1/submit").session(editor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("自己")));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = loginAdmin();
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
