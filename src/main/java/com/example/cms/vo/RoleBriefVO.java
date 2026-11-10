package com.example.cms.vo;

import java.io.Serializable;

/**
 * 用户列表中的角色简要信息。
 */
public class RoleBriefVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleName;
    private String roleCode;

    public RoleBriefVO() {
    }

    public RoleBriefVO(Long id, String roleName, String roleCode) {
        this.id = id;
        this.roleName = roleName;
        this.roleCode = roleCode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
