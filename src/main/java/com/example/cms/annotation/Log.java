package com.example.cms.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解，由 OperationLogAspect 自动落库。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /** 业务模块，如“文章管理”。 */
    String module();

    /** 操作名称，如“发布文章”。 */
    String operation();
}
