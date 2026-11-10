-- H2（MySQL 兼容模式）建表脚本，用于零配置演示与自动化测试。
DROP TABLE IF EXISTS sys_oper_log;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS cms_article;
DROP TABLE IF EXISTS cms_category;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    status TINYINT NOT NULL DEFAULT 1,
    last_login_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_username ON sys_user (username);
CREATE INDEX idx_user_status ON sys_user (status);

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(30) NOT NULL,
    role_code VARCHAR(30) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(200),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_role_code ON sys_role (role_code);

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    perm_name VARCHAR(50) NOT NULL,
    perm_code VARCHAR(50) NOT NULL,
    uri VARCHAR(200),
    method VARCHAR(10),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_perm_code ON sys_permission (perm_code);

CREATE TABLE sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
);
CREATE UNIQUE INDEX uk_user_role ON sys_user_role (user_id, role_id);

CREATE TABLE sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
);
CREATE UNIQUE INDEX uk_role_perm ON sys_role_permission (role_id, permission_id);

CREATE TABLE cms_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    description VARCHAR(200),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_by BIGINT,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_category_parent_sort ON cms_category (parent_id, sort);

CREATE TABLE cms_article (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    summary VARCHAR(500),
    content CLOB NOT NULL,
    category_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    audit_remark VARCHAR(500),
    audit_by BIGINT,
    audit_time DATETIME,
    publish_time DATETIME,
    view_count INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_by BIGINT,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_article_category FOREIGN KEY (category_id) REFERENCES cms_category (id),
    CONSTRAINT fk_article_author FOREIGN KEY (author_id) REFERENCES sys_user (id)
);
CREATE INDEX idx_article_title ON cms_article (title);
CREATE INDEX idx_article_category_status ON cms_article (category_id, status);
CREATE INDEX idx_article_publish_time ON cms_article (publish_time);
CREATE INDEX idx_article_author ON cms_article (author_id);

CREATE TABLE sys_oper_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    username VARCHAR(50),
    module VARCHAR(50),
    operation VARCHAR(100),
    method VARCHAR(200),
    request_uri VARCHAR(200),
    request_method VARCHAR(10),
    params VARCHAR(2000),
    ip VARCHAR(64),
    cost_time BIGINT,
    success TINYINT NOT NULL DEFAULT 1,
    error_msg VARCHAR(1000),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);
CREATE INDEX idx_log_user_time ON sys_oper_log (user_id, create_time);
CREATE INDEX idx_log_create_time ON sys_oper_log (create_time);
