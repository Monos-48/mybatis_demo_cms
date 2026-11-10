package com.example.cms.aspect;

import com.example.cms.annotation.Log;
import com.example.cms.common.LoginUser;
import com.example.cms.dto.LoginDTO;
import com.example.cms.entity.SysOperLog;
import com.example.cms.mapper.SysOperLogMapper;
import com.example.cms.util.IpUtils;
import com.example.cms.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面：记录操作人、模块、方法、参数、IP、耗时与成功状态。
 * 日志写入失败不影响主业务。
 */
@Aspect
@Component
@Order(0)
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);
    private static final int MAX_PARAMS_LENGTH = 2000;
    private static final String PASSWORD_MASK = "(\"(?:password|newPassword|oldPassword|passwordConfirm)\"\\s*:\\s*\")[^\"]*(\")";

    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(SysOperLogMapper operLogMapper, ObjectMapper objectMapper) {
        this.operLogMapper = operLogMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(operationLog)")
    public Object recordLog(ProceedingJoinPoint joinPoint, Log operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        int success = 1;
        String errorMessage = null;
        // 在业务执行前捕获身份，避免退出登录后无法记录操作人
        LoginUser actor = SecurityUtils.getLoginUser();
        String anonymousUsername = actor == null ? extractUsername(joinPoint.getArgs()) : null;
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            success = 0;
            errorMessage = throwable.getMessage();
            throw throwable;
        } finally {
            try {
                // 登录接口在方法执行后才建立会话，此处再取一次以记录登录人
                LoginUser resolvedActor = actor != null ? actor : SecurityUtils.getLoginUser();
                saveLog(joinPoint, operationLog, resolvedActor, anonymousUsername,
                        System.currentTimeMillis() - start, success, errorMessage);
            } catch (Exception ex) {
                log.warn("操作日志写入失败，不影响主流程", ex);
            }
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, Log annotation, LoginUser actor,
                         String anonymousUsername, long costTime, int success, String errorMessage) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        SysOperLog operLog = new SysOperLog();
        operLog.setModule(annotation.module());
        operLog.setOperation(annotation.operation());
        operLog.setMethod(signature.getDeclaringType().getSimpleName() + "." + signature.getName());
        operLog.setCostTime(costTime);
        operLog.setSuccess(success);
        operLog.setErrorMsg(truncate(errorMessage, 1000));

        if (actor != null) {
            operLog.setUserId(actor.getId());
            operLog.setUsername(actor.getUsername());
        } else {
            operLog.setUsername(anonymousUsername);
        }

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            operLog.setRequestUri(request.getRequestURI());
            operLog.setRequestMethod(request.getMethod());
            operLog.setIp(IpUtils.getClientIp(request));
        }
        operLog.setParams(serializeParams(joinPoint.getArgs()));
        operLogMapper.insert(operLog);
    }

    private String extractUsername(Object[] args) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof LoginDTO) {
                    return ((LoginDTO) arg).getUsername();
                }
            }
        }
        return "anonymous";
    }

    private String serializeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Object> loggable = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof ServletRequest || arg instanceof ServletResponse
                    || arg instanceof MultipartFile
                    || arg.getClass().getName().startsWith("org.springframework.validation")) {
                continue;
            }
            loggable.add(arg);
        }
        try {
            String json = objectMapper.writeValueAsString(loggable);
            json = json.replaceAll(PASSWORD_MASK, "$1******$2");
            return truncate(json, MAX_PARAMS_LENGTH);
        } catch (Exception ex) {
            return "[参数序列化失败]";
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
