package com.example.cms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cms.common.PageVO;
import com.example.cms.common.UserStatus;
import com.example.cms.dto.UserCreateDTO;
import com.example.cms.dto.UserQueryDTO;
import com.example.cms.entity.SysRole;
import com.example.cms.entity.SysUser;
import com.example.cms.entity.SysUserRole;
import com.example.cms.exception.BusinessException;
import com.example.cms.exception.NotFoundException;
import com.example.cms.mapper.SysRoleMapper;
import com.example.cms.mapper.SysUserMapper;
import com.example.cms.mapper.SysUserRoleMapper;
import com.example.cms.service.UserService;
import com.example.cms.util.PageUtils;
import com.example.cms.util.SecurityUtils;
import com.example.cms.vo.RoleBriefVO;
import com.example.cms.vo.UserRoleView;
import com.example.cms.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * 用户管理业务实现。角色列表采用批量查询后在内存组装，避免 N+1。
 */
@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageVO<UserVO> page(UserQueryDTO query) {
        Page<UserVO> page = PageUtils.toPage(query);
        IPage<UserVO> result = userMapper.selectUserPage(page, query);
        List<UserVO> records = result.getRecords();
        for (UserVO user : records) {
            user.setStatusText(UserStatus.descriptionOf(user.getStatus()));
        }
        fillRoles(records);
        return PageVO.of(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO create(UserCreateDTO dto) {
        String username = dto.getUsername().trim();
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(409, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname().trim());
        user.setEmail(dto.getEmail());
        user.setStatus(dto.getStatus() == null ? UserStatus.ENABLED.getCode() : dto.getStatus());
        userMapper.insert(user);

        List<Long> roleIds = distinct(dto.getRoleIds());
        validateRoles(roleIds);
        for (Long roleId : roleIds) {
            userRoleMapper.insert(new SysUserRole(user.getId(), roleId));
        }
        return getDetail(user.getId());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SysUser user = requireUser(id);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (id.equals(currentUserId) && status != null && status == UserStatus.DISABLED.getCode()) {
            throw new BusinessException("不能禁用当前登录账号");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setStatus(status);
        userMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoles(Long id, List<Long> roleIds) {
        requireUser(id);
        if (id.equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("不能修改当前登录账号的角色，请由其他管理员操作");
        }
        List<Long> ids = distinct(roleIds);
        validateRoles(ids);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        for (Long roleId : ids) {
            userRoleMapper.insert(new SysUserRole(id, roleId));
        }
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        requireUser(id);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireUser(id);
        if (id.equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
    }

    @Override
    public UserVO getDetail(Long id) {
        SysUser user = requireUser(id);
        UserVO vo = toUserVO(user);
        fillRoles(Collections.singletonList(vo));
        return vo;
    }

    private void fillRoles(List<UserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(UserVO::getId).collect(Collectors.toList());
        List<UserRoleView> rows = userMapper.selectRolesByUserIds(userIds);
        Map<Long, List<UserRoleView>> grouped = rows.stream()
                .collect(Collectors.groupingBy(UserRoleView::getUserId));
        for (UserVO user : users) {
            List<UserRoleView> userRoles = grouped.getOrDefault(user.getId(), Collections.emptyList());
            List<RoleBriefVO> roles = new ArrayList<>();
            List<Long> roleIds = new ArrayList<>();
            for (UserRoleView row : userRoles) {
                roles.add(new RoleBriefVO(row.getRoleId(), row.getRoleName(), row.getRoleCode()));
                roleIds.add(row.getRoleId());
            }
            user.setRoles(roles);
            user.setRoleIds(roleIds);
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    private void validateRoles(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException("包含不存在的角色");
        }
        for (SysRole role : roles) {
            if (role.getStatus() == null || role.getStatus() != 1) {
                throw new BusinessException("角色已停用：" + role.getRoleName());
            }
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

    private UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setStatusText(UserStatus.descriptionOf(user.getStatus()));
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
