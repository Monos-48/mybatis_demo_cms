package com.example.cms.controller;

import com.example.cms.annotation.Log;
import com.example.cms.annotation.RequirePermission;
import com.example.cms.common.ResultVO;
import com.example.cms.dto.RolePermissionDTO;
import com.example.cms.dto.RoleSaveDTO;
import com.example.cms.service.RoleService;
import com.example.cms.vo.RoleVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 角色与权限管理接口（管理员）。
 */
@RestController
@RequestMapping("/api/roles")
@Validated
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequirePermission("role:manage")
    public ResultVO<List<RoleVO>> list() {
        return ResultVO.success(roleService.listRoles());
    }

    @PostMapping
    @RequirePermission("role:manage")
    @Log(module = "角色管理", operation = "新增角色")
    public ResultVO<RoleVO> create(@Valid @RequestBody RoleSaveDTO dto) {
        return ResultVO.success("角色创建成功", roleService.create(dto));
    }

    @PutMapping("/{id}")
    @RequirePermission("role:manage")
    @Log(module = "角色管理", operation = "编辑角色")
    public ResultVO<RoleVO> update(@PathVariable Long id,
                                   @Valid @RequestBody RoleSaveDTO dto) {
        return ResultVO.success(roleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("role:manage")
    @Log(module = "角色管理", operation = "删除角色")
    public ResultVO<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResultVO.success();
    }

    @PutMapping("/{id}/permissions")
    @RequirePermission("role:manage")
    @Log(module = "角色管理", operation = "分配角色权限")
    public ResultVO<Void> assignPermissions(@PathVariable Long id,
                                            @Valid @RequestBody RolePermissionDTO dto) {
        roleService.assignPermissions(id, dto.getPermissionIds());
        return ResultVO.success();
    }
}
