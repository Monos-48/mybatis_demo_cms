-- ============================================================
-- CMS 内容管理系统 MySQL 9.5 建库建表脚本
-- 执行方式：mysql -uroot -p < sql/01_schema.sql
-- 兼容 MySQL 8.0+；当前目标版本为 MySQL 9.5（Connector/J 9.5.0）
-- ============================================================
CREATE DATABASE IF NOT EXISTS cms_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE cms_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS sys_oper_log;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS cms_article;
DROP TABLE IF EXISTS cms_category;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL COMMENT '登录名',
    password VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    nickname VARCHAR(50) NOT NULL COMMENT '昵称',
    email VARCHAR(100) NULL COMMENT '邮箱',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 禁用 1 正常 2 待审批',
    last_login_time DATETIME NULL COMMENT '最后登录时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户';

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_name VARCHAR(30) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(30) NOT NULL COMMENT '角色编码',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 停用 1 启用',
    remark VARCHAR(200) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    perm_name VARCHAR(50) NOT NULL COMMENT '权限名称',
    perm_code VARCHAR(50) NOT NULL COMMENT '权限码',
    uri VARCHAR(200) NULL COMMENT '接口 URI',
    method VARCHAR(10) NULL COMMENT 'HTTP 方法',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '权限点';

CREATE TABLE sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色关联';

CREATE TABLE sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    permission_id BIGINT NOT NULL COMMENT '权限 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-权限关联';

CREATE TABLE cms_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分类 ID，0 为顶级',
    sort INT NOT NULL DEFAULT 0 COMMENT '同级排序值',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0 停用 1 启用',
    description VARCHAR(200) NULL COMMENT '分类描述',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by BIGINT NULL COMMENT '创建人',
    update_by BIGINT NULL COMMENT '更新人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_category_parent_sort (parent_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '内容分类';

CREATE TABLE cms_article (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    title VARCHAR(100) NOT NULL COMMENT '标题',
    summary VARCHAR(500) NULL COMMENT '摘要',
    content LONGTEXT NOT NULL COMMENT '正文',
    category_id BIGINT NOT NULL COMMENT '分类 ID',
    author_id BIGINT NOT NULL COMMENT '作者 ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 草稿 1 待审核 2 审核通过 3 驳回 4 已发布 5 已下架',
    audit_remark VARCHAR(500) NULL COMMENT '审核意见',
    audit_by BIGINT NULL COMMENT '审核人',
    audit_time DATETIME NULL COMMENT '审核时间',
    publish_time DATETIME NULL COMMENT '发布时间',
    view_count INT NOT NULL DEFAULT 0 COMMENT '阅读量',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by BIGINT NULL COMMENT '创建人',
    update_by BIGINT NULL COMMENT '更新人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_article_title (title(50)),
    KEY idx_article_category_status (category_id, status),
    KEY idx_article_publish_time (publish_time),
    KEY idx_article_author (author_id),
    CONSTRAINT fk_article_category FOREIGN KEY (category_id) REFERENCES cms_category (id),
    CONSTRAINT fk_article_author FOREIGN KEY (author_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文章内容';

CREATE TABLE sys_oper_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NULL COMMENT '操作人 ID',
    username VARCHAR(50) NULL COMMENT '操作人用户名',
    module VARCHAR(50) NULL COMMENT '业务模块',
    operation VARCHAR(100) NULL COMMENT '操作名称',
    method VARCHAR(200) NULL COMMENT '目标方法',
    request_uri VARCHAR(200) NULL COMMENT '请求 URI',
    request_method VARCHAR(10) NULL COMMENT 'HTTP 方法',
    params VARCHAR(2000) NULL COMMENT '脱敏后的请求参数',
    ip VARCHAR(64) NULL COMMENT '客户端 IP',
    cost_time BIGINT NULL COMMENT '耗时（毫秒）',
    success TINYINT NOT NULL DEFAULT 1 COMMENT '1 成功 0 失败',
    error_msg VARCHAR(1000) NULL COMMENT '失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_log_user_time (user_id, create_time),
    KEY idx_log_create_time (create_time),
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志';
