package com.example.cms.service;

import com.example.cms.dto.RoleSaveDTO;
import com.example.cms.vo.PermissionVO;
import com.example.cms.vo.RoleVO;

import java.util.List;

/**
 * 角色与权限管理业务。
 */
public interface RoleService {

    List<RoleVO> listRoles();

    RoleVO create(RoleSaveDTO dto);

    RoleVO update(Long id, RoleSaveDTO dto);

    void delete(Long id);

    void assignPermissions(Long id, List<Long> permissionIds);

    List<PermissionVO> listPermissions();
}
