package com.example.cms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.cms.dto.RoleSaveDTO;
import com.example.cms.entity.SysPermission;
import com.example.cms.entity.SysRole;
import com.example.cms.entity.SysRolePermission;
import com.example.cms.entity.SysUserRole;
import com.example.cms.exception.BusinessException;
import com.example.cms.exception.NotFoundException;
import com.example.cms.mapper.SysPermissionMapper;
import com.example.cms.mapper.SysRoleMapper;
import com.example.cms.mapper.SysRolePermissionMapper;
import com.example.cms.mapper.SysUserRoleMapper;
import com.example.cms.service.RoleService;
import com.example.cms.vo.PermissionVO;
import com.example.cms.vo.RoleVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色与权限管理实现。
 */
@Service
public class RoleServiceImpl implements RoleService {

    /** 内置管理员角色必须保留的恢复权限，防止误操作后无人能再进入管理界面。 */
    private static final Set<String> ADMIN_REQUIRED_PERMISSIONS =
            Set.of("user:manage", "role:manage");

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    public RoleServiceImpl(SysRoleMapper roleMapper, SysRolePermissionMapper rolePermissionMapper,
                           SysPermissionMapper permissionMapper, SysUserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public List<RoleVO> listRoles() {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getId));
        List<SysRolePermission> links = rolePermissionMapper.selectList(null);
        Map<Long, List<Long>> grouped = links.stream()
                .collect(Collectors.groupingBy(SysRolePermission::getRoleId,
                        Collectors.mapping(SysRolePermission::getPermissionId, Collectors.toList())));
        List<RoleVO> result = new ArrayList<>();
        for (SysRole role : roles) {
            RoleVO vo = toRoleVO(role);
            vo.setPermissionIds(grouped.getOrDefault(role.getId(), Collections.emptyList()));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO create(RoleSaveDTO dto) {
        ensureRoleCodeUnique(dto.getRoleCode(), null);
        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName().trim());
        role.setRoleCode(dto.getRoleCode().trim());
        role.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        role.setRemark(dto.getRemark());
        roleMapper.insert(role);
        return toRoleVO(role);
    }

    @Override
    public RoleVO update(Long id, RoleSaveDTO dto) {
        SysRole role = requireRole(id);
        boolean builtinAdmin = "ADMIN".equals(role.getRoleCode());
        if (builtinAdmin && !"ADMIN".equals(dto.getRoleCode())) {
            throw new BusinessException("内置管理员角色编码不可修改");
        }
        if (builtinAdmin && dto.getStatus() != null && dto.getStatus() != 1) {
            throw new BusinessException("内置管理员角色不可停用");
        }
        ensureRoleCodeUnique(dto.getRoleCode(), id);
        role.setRoleName(dto.getRoleName().trim());
        role.setRoleCode(dto.getRoleCode().trim());
        role.setStatus(dto.getStatus() == null ? role.getStatus() : dto.getStatus());
        role.setRemark(dto.getRemark());
        roleMapper.updateById(role);
        RoleVO vo = toRoleVO(role);
        vo.setPermissionIds(roleMapper.selectPermissionIdsByRoleId(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if ("ADMIN".equals(role.getRoleCode())) {
            throw new BusinessException("内置管理员角色不可删除");
        }
        Long bindCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (bindCount != null && bindCount > 0) {
            throw new BusinessException("该角色仍绑定用户，请先解除关联");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long id, List<Long> permissionIds) {
        SysRole role = requireRole(id);
        List<Long> ids = distinct(permissionIds);
        List<SysPermission> permissions = ids.isEmpty()
                ? Collections.emptyList() : permissionMapper.selectBatchIds(ids);
        if (permissions.size() != ids.size()) {
            throw new BusinessException("包含不存在的权限点");
        }
        if ("ADMIN".equals(role.getRoleCode())) {
            Set<String> codes = permissions.stream()
                    .map(SysPermission::getPermCode)
                    .collect(Collectors.toSet());
            if (!codes.containsAll(ADMIN_REQUIRED_PERMISSIONS)) {
                throw new BusinessException("内置管理员角色必须保留用户管理与角色权限管理权限");
            }
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
        for (Long permissionId : ids) {
            rolePermissionMapper.insert(new SysRolePermission(id, permissionId));
        }
    }

    @Override
    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getId))
                .stream()
                .map(this::toPermissionVO)
                .collect(Collectors.toList());
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new NotFoundException("角色不存在");
        }
        return role;
    }

    private void ensureRoleCodeUnique(String roleCode, Long excludeId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode);
        if (excludeId != null) {
            wrapper.ne(SysRole::getId, excludeId);
        }
        Long count = roleMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(409, "角色编码已存在");
        }
    }

    private List<Long> distinct(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        return new ArrayList<>(unique);
    }

    private RoleVO toRoleVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleCode(role.getRoleCode());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setCreateTime(role.getCreateTime());
        return vo;
    }

    private PermissionVO toPermissionVO(SysPermission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setPermName(permission.getPermName());
        vo.setPermCode(permission.getPermCode());
        vo.setUri(permission.getUri());
        vo.setMethod(permission.getMethod());
        return vo;
    }
}
