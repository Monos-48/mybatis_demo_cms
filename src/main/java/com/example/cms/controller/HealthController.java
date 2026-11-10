package com.example.cms.controller;

import com.example.cms.common.ResultVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查，供部署探活与 ApiFox 环境验证。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResultVO<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("application", "mybatis_demo_cms");
        data.put("time", LocalDateTime.now());
        return ResultVO.success(data);
    }
}
