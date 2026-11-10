package com.example.cms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.cms.common.LoginUser;
import com.example.cms.common.UserStatus;
import com.example.cms.dto.LoginDTO;
import com.example.cms.dto.RegisterDTO;
import com.example.cms.entity.SysRole;
import com.example.cms.entity.SysUser;
import com.example.cms.entity.SysUserRole;
import com.example.cms.exception.BusinessException;
import com.example.cms.exception.NotFoundException;
import com.example.cms.exception.UnauthorizedException;
import com.example.cms.mapper.SysPermissionMapper;
import com.example.cms.mapper.SysRoleMapper;
import com.example.cms.mapper.SysUserMapper;
import com.example.cms.mapper.SysUserRoleMapper;
import com.example.cms.service.AuthService;
import com.example.cms.util.SecurityUtils;
import com.example.cms.vo.LoginVO;
import com.example.cms.vo.RoleBriefVO;
import com.example.cms.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证业务实现：BCrypt 校验、Session 登录、注册审批。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysUserRoleMapper userRoleMapper, SysPermissionMapper permissionMapper,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionMapper = permissionMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public LoginVO login(LoginDTO dto, HttpServletRequest request) {
        String username = dto.getUsername().trim();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() == UserStatus.DISABLED.getCode()) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        if (user.getStatus() == UserStatus.PENDING.getCode()) {
            throw new BusinessException(403, "账号正在等待管理员审批，暂不可登录");
        }

        Set<String> roles = new HashSet<>(userMapper.selectRoleCodesByUserId(user.getId()));
        Set<String> permissions = new HashSet<>(
                permissionMapper.selectPermissionCodesByUserId(user.getId()));

        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setRoles(roles);
        loginUser.setPermissions(permissions);

        // 登录成功后更换 Session，防止会话固定攻击
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        request.getSession(true).setAttribute(SecurityUtils.SESSION_USER_KEY, loginUser);

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(update);

        return toLoginVO(user, roles, permissions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterDTO dto) {
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
        user.setStatus(UserStatus.PENDING.getCode());
        userMapper.insert(user);

        // 注册用户默认预分配作者角色，审批通过后即可使用
        SysRole authorRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "AUTHOR")
                .eq(SysRole::getStatus, 1));
        UserVO vo = toUserVO(user);
        if (authorRole != null) {
            userRoleMapper.insert(new SysUserRole(user.getId(), authorRole.getId()));
            vo.getRoleIds().add(authorRole.getId());
            vo.getRoles().add(new RoleBriefVO(authorRole.getId(),
                    authorRole.getRoleName(), authorRole.getRoleCode()));
        }
        return vo;
    }

    @Override
    public LoginVO currentUser() {
        LoginUser sessionUser = SecurityUtils.requireLoginUser();
        SysUser user = userMapper.selectById(sessionUser.getId());
        if (user == null) {
            SecurityUtils.clearSession();
            throw new UnauthorizedException();
        }
        Set<String> roles = new HashSet<>(userMapper.selectRoleCodesByUserId(user.getId()));
        Set<String> permissions = new HashSet<>(
                permissionMapper.selectPermissionCodesByUserId(user.getId()));

        sessionUser.setUsername(user.getUsername());
        sessionUser.setNickname(user.getNickname());
        sessionUser.setRoles(roles);
        sessionUser.setPermissions(permissions);

        return toLoginVO(user, roles, permissions);
    }

    @Override
    public void logout() {
        SecurityUtils.clearSession();
    }

    private LoginVO toLoginVO(SysUser user, Set<String> roles, Set<String> permissions) {
        LoginVO vo = new LoginVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        return vo;
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
