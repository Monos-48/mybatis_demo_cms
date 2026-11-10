package com.example.cms.vo;

import java.io.Serializable;

/**
 * 批量查询用户角色时的扁平结果行，供 Service 内存组装，避免 N+1。
 */
public class UserRoleView implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long roleId;
    private String roleName;
    private String roleCode;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}
