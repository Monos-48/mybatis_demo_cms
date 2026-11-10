-- ============================================================
-- CMS 初始化数据（MySQL）
-- 执行前请先执行 01_schema.sql
-- ============================================================
USE cms_db;


INSERT INTO sys_permission (id, perm_name, perm_code, uri, method, create_time) VALUES
(1, '查看文章列表', 'article:list', 'GET /api/articles', 'GET', '2026-10-01 09:00:00'),
(2, '新增文章', 'article:create', 'POST /api/articles', 'POST', '2026-10-01 09:00:00'),
(3, '编辑文章', 'article:edit', 'PUT /api/articles/{id}', 'PUT', '2026-10-01 09:00:00'),
(4, '删除文章', 'article:delete', 'DELETE /api/articles/{id}', 'DELETE', '2026-10-01 09:00:00'),
(5, '提交审核', 'article:submit', 'POST /api/articles/{id}/submit', 'POST', '2026-10-01 09:00:00'),
(6, '审核文章', 'article:audit', 'POST /api/articles/{id}/audit', 'POST', '2026-10-01 09:00:00'),
(7, '发布文章', 'article:publish', 'POST /api/articles/{id}/publish', 'POST', '2026-10-01 09:00:00'),
(8, '下架文章', 'article:offline', 'POST /api/articles/{id}/offline', 'POST', '2026-10-01 09:00:00'),
(9, '查看分类', 'category:list', 'GET /api/categories/tree', 'GET', '2026-10-01 09:00:00'),
(10, '管理分类', 'category:manage', '/api/categories/**', 'ALL', '2026-10-01 09:00:00'),
(11, '用户管理', 'user:manage', '/api/users/**', 'ALL', '2026-10-01 09:00:00'),
(12, '角色权限管理', 'role:manage', '/api/roles/**', 'ALL', '2026-10-01 09:00:00'),
(13, '查看操作日志', 'log:view', 'GET /api/logs', 'GET', '2026-10-01 09:00:00');

INSERT INTO sys_role (id, role_name, role_code, status, remark, create_time, update_time) VALUES
(1, '超级管理员', 'ADMIN', 1, '拥有全部权限，维护用户与角色', '2026-10-01 09:00:00', '2026-10-01 09:00:00'),
(2, '内容编辑', 'EDITOR', 1, '负责文章审核、发布、分类维护', '2026-10-01 09:00:00', '2026-10-01 09:00:00'),
(3, '内容作者', 'AUTHOR', 1, '创作、编辑并提交自己的文章', '2026-10-01 09:00:00', '2026-10-01 09:00:00'),
(4, '只读访客', 'VIEWER', 1, '只读查看文章与分类', '2026-10-01 09:00:00', '2026-10-01 09:00:00');

INSERT INTO sys_user (id, username, password, nickname, email, status, last_login_time, create_time, update_time) VALUES
(1, 'admin', '$2a$10$n6UfmihL8pyqfzhs2Mak1.pL.nuMQXxmddiCvlcaE47QnZaHaYPTy', '系统管理员', 'admin@cms.local', 1, NULL, '2026-10-01 09:00:00', '2026-10-01 09:00:00'),
(2, 'editor', '$2a$10$HUYNi6gzDnm6HJuskqBNiOEcp9VFtR/jA2dJtzKhogZW0pLzjL0Na', '内容主编', 'editor@cms.local', 1, NULL, '2026-10-01 09:05:00', '2026-10-01 09:05:00'),
(3, 'author', '$2a$10$HaKMTd4ICi9KuO841IH5wuMrIhoVDRx91WP2hUnuFKUhLm2szcmh2', '专栏作者', 'author@cms.local', 1, NULL, '2026-10-01 09:10:00', '2026-10-01 09:10:00'),
(4, 'viewer', '$2a$10$1b2xTgzTJMBVugK06tRLIOOitRkuTeMRgsQZEiGhAreESzvNGBx4q', '运营观察员', 'viewer@cms.local', 1, NULL, '2026-10-01 09:15:00', '2026-10-01 09:15:00'),
(5, 'pending', '$2a$10$yJi/NhmzGzjafqplFawDSOHpAb9se2sJY6pinigaZ.KZyyVY2kdHO', '待审批用户', 'pending@cms.local', 2, NULL, '2026-11-08 14:30:00', '2026-11-08 14:30:00');

INSERT INTO sys_user_role (id, user_id, role_id) VALUES
(1, 1, 1),
(2, 2, 2),
(3, 3, 3),
(4, 4, 4),
(5, 5, 3);

INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9), (1, 10), (1, 11), (1, 12), (1, 13),
(2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8), (2, 9), (2, 10),
(3, 1), (3, 2), (3, 3), (3, 4), (3, 5), (3, 9),
(4, 1), (4, 9);

INSERT INTO cms_category (id, name, parent_id, sort, status, description, create_time, update_time, create_by, update_by) VALUES
(1, '技术资讯', 0, 1, 1, '技术领域新闻与深度解读', '2026-10-01 10:00:00', '2026-10-01 10:00:00', 1, 1),
(2, '产品动态', 0, 2, 1, '产品版本与运营活动', '2026-10-01 10:01:00', '2026-10-01 10:01:00', 1, 1),
(3, '前端开发', 1, 1, 1, 'Web 前端工程实践', '2026-10-01 10:02:00', '2026-10-01 10:02:00', 1, 1),
(4, '后端开发', 1, 2, 1, 'Java 服务端与数据库', '2026-10-01 10:03:00', '2026-10-01 10:03:00', 1, 1),
(5, '人工智能', 1, 3, 1, 'AI 应用与工程化', '2026-10-01 10:04:00', '2026-10-01 10:04:00', 1, 1),
(6, '公司新闻', 2, 1, 1, '企业公告与品牌活动', '2026-10-01 10:05:00', '2026-10-01 10:05:00', 1, 1),
(7, '历史归档', 0, 9, 0, '已停用的历史栏目', '2026-10-01 10:06:00', '2026-10-01 10:06:00', 1, 1);

INSERT INTO cms_article (id, title, summary, content, category_id, author_id, status, audit_remark, audit_by, audit_time, publish_time, view_count, version, create_time, update_time, create_by, update_by) VALUES
(1, 'MyBatis-Plus 分页与条件构造器实践', '通过分页插件与 LambdaQueryWrapper 构建可组合的内容检索能力。',
 'MyBatis-Plus 提供的分页插件可以在不侵入业务 SQL 的前提下完成物理分页。本文从分页参数、动态条件、排序白名单三个方面介绍内容列表的实现方式，并说明如何避免 ORDER BY 注入。',
 4, 3, 2, '内容完整，审核通过', 2, '2026-11-02 10:20:00', NULL, 128, 0, '2026-11-01 09:20:00', '2026-11-02 10:20:00', 3, 2),
(2, '企业级 CMS 的 RBAC 权限模型设计', '从用户、角色、权限三个维度构建接口级访问控制。',
 'RBAC 模型通过用户-角色、角色-权限两张关联表建立多对多关系。请求进入 Controller 后，由 Spring AOP 解析当前会话用户的权限码集合，与接口声明的权限码比对，实现细粒度权限拦截。',
 4, 2, 4, '审核通过，已安排发布', 1, '2026-11-04 15:00:00', '2026-11-05 09:00:00', 356, 0, '2026-11-03 11:30:00', '2026-11-05 09:00:00', 2, 2),
(3, 'Vue 管理后台的渐进式改造', '在不重写业务的前提下，逐步引入组件化与状态管理。',
 '管理后台的改造应当以业务连续性为前提。可以先把公共布局与表格抽成组件，再引入统一请求层和状态管理，最后渐进替换页面，避免一次性重写带来的风险。',
 3, 3, 1, NULL, NULL, NULL, NULL, 42, 0, '2026-11-07 14:10:00', '2026-11-07 14:10:00', 3, 3),
(4, '2026 年内容运营趋势观察', '内容中台、智能分发与合规审计成为企业内容运营的关键词。',
 '随着内容规模增长，企业开始建设内容中台，统一管理素材、栏目与发布渠道。同时，审核流程和操作审计成为合规要求的一部分，内容系统需要提供完整的日志与权限能力。',
 6, 2, 3, '选题与栏目定位不符，请补充数据来源后重新提交。', 1, '2026-11-06 16:40:00', NULL, 18, 0, '2026-11-06 10:00:00', '2026-11-06 16:40:00', 2, 1),
(5, 'Spring AOP 实现接口级权限拦截', '自定义注解 + 环绕通知，把权限校验从业务代码中解耦。',
 '通过自定义 RequirePermission 注目标记需要保护的接口，再以 Around 通知读取注解参数，查询当前用户的权限码集合。缺少权限时抛出自定义异常，由全局异常处理器统一返回 403。',
 4, 3, 0, NULL, NULL, NULL, NULL, 0, 0, '2026-11-09 09:40:00', '2026-11-09 09:40:00', 3, 3),
(6, '旧版编辑规范（归档）', '已停用栏目下的历史文章，仅保留查阅。',
 '本规范已被新版内容编辑手册替代。文章保留用于历史追溯，分类处于停用状态，不允许新文章继续挂载。',
 7, 3, 5, '历史内容，转入归档', 2, '2026-10-20 11:00:00', '2026-10-21 09:00:00', 76, 0, '2026-10-19 16:20:00', '2026-10-25 10:00:00', 3, 2);

INSERT INTO sys_oper_log (user_id, username, module, operation, method, request_uri, request_method, params, ip, cost_time, success, error_msg, create_time) VALUES
(1, 'admin', '认证管理', '用户登录', 'AuthController.login', '/api/auth/login', 'POST', '[{"username":"admin","password":"******"}]', '127.0.0.1', 35, 1, NULL, '2026-11-10 08:55:00'),
(2, 'editor', '文章管理', '审核文章', 'ArticleController.audit', '/api/articles/1/audit', 'POST', '[1,{"approved":true,"remark":"内容完整，审核通过"}]', '127.0.0.1', 48, 1, NULL, '2026-11-10 09:10:00'),
(3, 'author', '文章管理', '提交审核', 'ArticleController.submit', '/api/articles/3/submit', 'POST', '[3]', '127.0.0.1', 22, 1, NULL, '2026-11-10 09:20:00'),
(4, 'viewer', '文章管理', '删除文章', 'ArticleController.delete', '/api/articles/2', 'DELETE', '[2]', '127.0.0.1', 12, 0, '没有操作权限：article:delete', '2026-11-10 09:30:00');
