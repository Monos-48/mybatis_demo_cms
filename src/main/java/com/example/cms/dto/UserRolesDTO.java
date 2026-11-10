package com.example.cms.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户角色分配请求。
 */
public class UserRolesDTO {

    @NotNull(message = "角色列表不能为空")
    @Size(max = 10, message = "单个用户最多绑定 10 个角色")
    private List<Long> roleIds = new ArrayList<>();

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds == null ? new ArrayList<>() : roleIds;
    }
}
