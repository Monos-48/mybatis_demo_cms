package com.example.cms.controller;

import com.example.cms.annotation.RequirePermission;
import com.example.cms.common.PageVO;
import com.example.cms.common.ResultVO;
import com.example.cms.dto.LogQueryDTO;
import com.example.cms.service.OperationLogService;
import com.example.cms.vo.LogVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 操作日志查询接口。
 */
@RestController
@RequestMapping("/api/logs")
@Validated
public class LogController {

    private final OperationLogService operationLogService;

    public LogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    @RequirePermission("log:view")
    public ResultVO<PageVO<LogVO>> page(@Valid LogQueryDTO query) {
        return ResultVO.success(operationLogService.page(query));
    }
}
