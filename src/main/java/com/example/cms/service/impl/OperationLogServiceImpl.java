package com.example.cms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cms.common.LoginUser;
import com.example.cms.common.PageVO;
import com.example.cms.dto.LogQueryDTO;
import com.example.cms.entity.SysOperLog;
import com.example.cms.mapper.SysOperLogMapper;
import com.example.cms.service.OperationLogService;
import com.example.cms.util.IpUtils;
import com.example.cms.util.PageUtils;
import com.example.cms.util.SecurityUtils;
import com.example.cms.vo.LogVO;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志查询与手工记录实现。
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final SysOperLogMapper operLogMapper;

    public OperationLogServiceImpl(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @Override
    public PageVO<LogVO> page(LogQueryDTO query) {
        Page<SysOperLog> page = PageUtils.toPage(query);
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getModule() != null && !query.getModule().trim().isEmpty()) {
            wrapper.like(SysOperLog::getModule, query.getModule().trim());
        }
        if (query.getUsername() != null && !query.getUsername().trim().isEmpty()) {
            wrapper.like(SysOperLog::getUsername, query.getUsername().trim());
        }
        if (query.getSuccess() != null) {
            wrapper.eq(SysOperLog::getSuccess, query.getSuccess());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(SysOperLog::getCreateTime, query.getStartDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.lt(SysOperLog::getCreateTime, query.getEndDate().plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(SysOperLog::getCreateTime).orderByDesc(SysOperLog::getId);

        Page<SysOperLog> result = operLogMapper.selectPage(page, wrapper);
        List<LogVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void record(String module, String operation, String detail) {
        SysOperLog log = new SysOperLog();
        log.setModule(module);
        log.setOperation(operation);
        log.setMethod("manual");
        log.setParams(detail);
        log.setCostTime(0L);
        log.setSuccess(1);

        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser != null) {
            log.setUserId(loginUser.getId());
            log.setUsername(loginUser.getUsername());
        } else {
            log.setUsername("system");
        }

        HttpServletRequest request = SecurityUtils.getRequest();
        if (request != null) {
            log.setIp(IpUtils.getClientIp(request));
            log.setRequestUri(request.getRequestURI());
            log.setRequestMethod(request.getMethod());
        }
        operLogMapper.insert(log);
    }

    private LogVO toVO(SysOperLog entity) {
        LogVO vo = new LogVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setUsername(entity.getUsername());
        vo.setModule(entity.getModule());
        vo.setOperation(entity.getOperation());
        vo.setMethod(entity.getMethod());
        vo.setRequestUri(entity.getRequestUri());
        vo.setRequestMethod(entity.getRequestMethod());
        vo.setParams(entity.getParams());
        vo.setIp(entity.getIp());
        vo.setCostTime(entity.getCostTime());
        vo.setSuccess(entity.getSuccess());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
