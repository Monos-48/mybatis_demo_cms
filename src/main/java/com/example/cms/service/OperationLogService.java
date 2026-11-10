package com.example.cms.service;

import com.example.cms.common.PageVO;
import com.example.cms.dto.LogQueryDTO;
import com.example.cms.vo.LogVO;

/**
 * 操作日志业务。
 */
public interface OperationLogService {

    PageVO<LogVO> page(LogQueryDTO query);

    /**
     * 手工记录一条成功操作日志，用于批量操作逐条留痕。
     */
    void record(String module, String operation, String detail);
}
