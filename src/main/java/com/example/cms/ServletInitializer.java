package com.example.cms;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 外部 Servlet 容器（Tomcat 9）引导入口。
 * <p>部署 WAR 时由 Tomcat 调用本类，等价于执行 {@link CmsApplication#main(String[])}。
 * 注意：Spring Boot 2.7 基于 {@code javax.servlet}，只能部署到 Tomcat 9.x；
 * Tomcat 10+ 使用 {@code jakarta.servlet}，需要迁移到 Spring Boot 3。</p>
 */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(CmsApplication.class);
    }
}
