package com.example.cms;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 内置 ADMIN 角色的恢复通路保护：不能被停用，也不能移除用户/角色管理权限。
 */
class RoleProtectionIntegrationTest extends BaseIntegrationTest {

    @Test
    void builtinAdminRoleCannotBeDisabledOrLoseRecoveryPermissions() throws Exception {
        MockHttpSession admin = loginAdmin();

        mockMvc.perform(put("/api/roles/1")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"超级管理员\",\"roleCode\":\"ADMIN\",\"status\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("停用")));

        mockMvc.perform(put("/api/roles/1/permissions")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("管理权限")));

        mockMvc.perform(put("/api/roles/1/permissions")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[1,2,3]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("管理权限")));

        // 保留 user:manage / role:manage 时允许调整其它权限
        mockMvc.perform(put("/api/roles/1/permissions")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[11,12]}"))
                .andExpect(status().isOk());
    }
}
