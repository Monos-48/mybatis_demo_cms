package com.example.cms.aspect;

import com.example.cms.annotation.RequirePermission;
import com.example.cms.common.LoginUser;
import com.example.cms.exception.PermissionDeniedException;
import com.example.cms.exception.UnauthorizedException;
import com.example.cms.mapper.SysPermissionMapper;
import com.example.cms.util.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 接口级权限拦截切面。
 * 拦截标注 {@link RequirePermission} 的方法，从数据库加载当前用户权限码并逐一比对。
 */
@Aspect
@Component
@Order(1)
public class PermissionAspect {

    private final SysPermissionMapper permissionMapper;

    public PermissionAspect(SysPermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission)
            throws Throwable {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            throw new UnauthorizedException();
        }

        Set<String> granted = new HashSet<>(
                permissionMapper.selectPermissionCodesByUserId(loginUser.getId()));
        List<String> required = Arrays.asList(requirePermission.value());
        for (String permission : required) {
            if (!granted.contains(permission)) {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                throw new PermissionDeniedException(
                        "没有操作权限：" + permission + "（" + signature.getMethod().getName() + "）");
            }
        }
        return joinPoint.proceed();
    }
}
