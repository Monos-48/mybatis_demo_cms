package com.example.cms.controller;

import com.example.cms.annotation.RequirePermission;
import com.example.cms.common.ResultVO;
import com.example.cms.service.RoleService;
import com.example.cms.vo.PermissionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限点查询接口（管理员）。
 */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final RoleService roleService;

    public PermissionController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequirePermission("role:manage")
    public ResultVO<List<PermissionVO>> list() {
        return ResultVO.success(roleService.listPermissions());
    }
}
