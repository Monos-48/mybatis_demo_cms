package com.example.cms.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.example.cms.util.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 字段自动填充：createTime/updateTime 以及 createBy/updateBy 操作人。
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            strictInsertFill(metaObject, "createBy", Long.class, userId);
            strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新场景必须强制覆盖，strictUpdateFill 在字段已有值时不会生效
        if (metaObject.hasSetter("updateTime")) {
            setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null && metaObject.hasSetter("updateBy")) {
            setFieldValByName("updateBy", userId, metaObject);
        }
    }
}
