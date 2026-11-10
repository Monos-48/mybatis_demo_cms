package com.example.cms.controller;

import com.example.cms.annotation.Log;
import com.example.cms.annotation.RequirePermission;
import com.example.cms.common.PageVO;
import com.example.cms.common.ResultVO;
import com.example.cms.dto.PasswordResetDTO;
import com.example.cms.dto.UserCreateDTO;
import com.example.cms.dto.UserQueryDTO;
import com.example.cms.dto.UserRolesDTO;
import com.example.cms.dto.UserStatusDTO;
import com.example.cms.service.UserService;
import com.example.cms.vo.UserVO;
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

/**
 * 用户管理接口（管理员）。
 */
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequirePermission("user:manage")
    public ResultVO<PageVO<UserVO>> page(@Valid UserQueryDTO query) {
        return ResultVO.success(userService.page(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("user:manage")
    public ResultVO<UserVO> detail(@PathVariable Long id) {
        return ResultVO.success(userService.getDetail(id));
    }

    @PostMapping
    @RequirePermission("user:manage")
    @Log(module = "用户管理", operation = "新增用户")
    public ResultVO<UserVO> create(@Valid @RequestBody UserCreateDTO dto) {
        return ResultVO.success("用户创建成功", userService.create(dto));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("user:manage")
    @Log(module = "用户管理", operation = "变更用户状态")
    public ResultVO<Void> updateStatus(@PathVariable Long id,
                                       @Valid @RequestBody UserStatusDTO dto) {
        userService.updateStatus(id, dto.getStatus());
        return ResultVO.success();
    }

    @PutMapping("/{id}/roles")
    @RequirePermission("user:manage")
    @Log(module = "用户管理", operation = "分配用户角色")
    public ResultVO<Void> updateRoles(@PathVariable Long id,
                                      @Valid @RequestBody UserRolesDTO dto) {
        userService.updateRoles(id, dto.getRoleIds());
        return ResultVO.success();
    }

    @PutMapping("/{id}/password")
    @RequirePermission("user:manage")
    @Log(module = "用户管理", operation = "重置用户密码")
    public ResultVO<Void> resetPassword(@PathVariable Long id,
                                        @Valid @RequestBody PasswordResetDTO dto) {
        userService.resetPassword(id, dto.getNewPassword());
        return ResultVO.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("user:manage")
    @Log(module = "用户管理", operation = "删除用户")
    public ResultVO<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResultVO.success();
    }
}
