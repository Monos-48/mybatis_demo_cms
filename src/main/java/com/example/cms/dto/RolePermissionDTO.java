package com.example.cms.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色权限分配请求。
 */
public class RolePermissionDTO {

    @NotNull(message = "权限列表不能为空")
    @Size(max = 100, message = "单次最多分配 100 个权限")
    private List<Long> permissionIds = new ArrayList<>();

    public List<Long> getPermissionIds() {
        return permissionIds;
    }

    public void setPermissionIds(List<Long> permissionIds) {
        this.permissionIds = permissionIds == null ? new ArrayList<>() : permissionIds;
    }
}
