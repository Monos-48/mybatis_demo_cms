# 课程设计报告提纲与答辩指南

本文件用于把已完成的工程、文档和测试组织成课程设计报告。可直接按章节扩写，不需要再补做功能。截图、图表、测试记录和 AI 使用记录均可从仓库直接引用。

## 1. 报告基本信息

| 项目 | 建议填写 |
| --- | --- |
| 题目 | 基于 Spring Boot + MyBatis-Plus 的 CMS 内容管理系统设计与实现 |
| 副标题 | 面向文章审核与 RBAC 权限控制的企业级内容管理实践 |
| 学生/学号/班级 | 按实际填写 |
| 指导教师 | 按实际填写 |
| 完成日期 | 2026-11-11 |
| 代码仓库 | 本仓库根目录 |
| 运行方式 | H2 零配置演示 / MySQL 9.5 部署 |

### 摘要写作要点（300–500 字）

1. 背景：内容平台需要区分作者、编辑、管理员职责，并保证内容发布可审核、可追溯。
2. 方法：采用 Spring Boot 2.7（Spring 5.3）+ SpringMVC + MyBatis-Plus + MySQL/H2，按 Controller–Service–Mapper 分层；使用 RBAC 模型和 AOP 实现接口级权限与操作日志。
3. 关键工作：设计 8 张表、文章六状态状态机、乐观锁、逻辑删除、自动填充、批量操作事务、JOIN/批量 IN 消除 N+1、JSR-303 校验、BCrypt 与会话安全。
4. 结果：完成管理端、RESTful API、MySQL 脚本、22 个接口集成测试和浏览器全流程验收。
5. 结论：系统满足任务书功能与质量要求，并说明 MySQL 环境验证与高并发测试的边界。

关键词：内容管理系统；Spring Boot；MyBatis-Plus；RBAC；文章审核；AOP

## 2. 章节结构

### 第 1 章 绪论（建议 2–3 页）

- 1.1 项目背景与意义：内容生产、审核、发布分离；权限与审计的企业需求。
- 1.2 国内外研究/应用现状：传统 CMS、Headless CMS、开源系统对比（可简要）。
- 1.3 项目目标与主要工作：
  - 文章全生命周期；
  - 多级分类与高级检索；
  - RBAC 与接口级鉴权；
  - 操作日志与安全；
  - 可运行工程与测试文档。
- 1.4 论文组织结构。

**可引用材料**：README 第 1 节功能清单。

### 第 2 章 需求分析（建议 4–6 页）

- 2.1 角色与用例
  - ADMIN：全部权限；
  - EDITOR：文章审核、发布、分类管理；
  - AUTHOR：创作、提交、维护自己的草稿/驳回稿；
  - VIEWER：只读；
  - 待审批用户：可注册、不可登录。
  - 建议画用例图（可用 Mermaid 或 draw.io）。
- 2.2 功能需求
  - 文章管理、审核状态机、批量操作；
  - 分类树、防环、删除引用保护；
  - 用户审批/禁用/角色/重置密码；
  - 角色权限分配；
  - 日志检索与脱敏；
  - 高级检索条件与排序。
- 2.3 非功能需求
  - 安全：BCrypt、Session 防固定、预编译 SQL、排序白名单、统一异常；
  - 性能：分页、JOIN、批量 IN、索引；
  - 可维护性：分层、统一响应、Javadoc、配置分环境；
  - 兼容性：JDK 17、MySQL 9.5、H2。
- 2.4 约束与假设
  - Spring 5.x 约束；
  - 单级审核；
  - 正文为纯文本；
  - MySQL 凭据由部署者提供。

**可引用材料**：README 权限矩阵、`docs/API.md` 权限矩阵。

### 第 3 章 系统设计（建议 8–12 页）

- 3.1 总体架构
  - 分层架构图（`docs/ARCHITECTURE.md` 第 1 节）；
  - 请求链路时序图（第 2 节）；
  - 技术选型理由：Spring Boot 2.7.18 对应 Spring 5.3.31，兼容 `javax.validation` 与 MyBatis-Plus 3.5.x。
- 3.2 数据库设计
  - E-R 图（`docs/DATABASE.md` 第 1 节）；
  - 8 张表说明：6 张业务表 + 2 张关联表；
  - 主外键、唯一键、索引设计；
  - 逻辑删除与唯一键的取舍。
- 3.3 权限设计
  - RBAC 数据模型；
  - `@RequirePermission` + AOP 校验流程；
  - 权限码实时查库、修改即时生效；
  - 前端菜单隐藏与服务端强制的双层边界。
- 3.4 文章状态机
  - 六状态定义；
  - 状态迁移表与状态图（`docs/ARCHITECTURE.md` 第 4 节）；
  - 所有权规则、审核意见必填、乐观锁。
- 3.5 关键流程
  - 登录/会话时序；
  - 审核流程；
  - 批量发布事务；
  - 操作日志切面。
- 3.6 接口设计
  - RESTful 资源命名；
  - 统一 `ResultVO`；
  - 状态码约定；
  - OpenAPI 规范。

**可引用材料**：`docs/ARCHITECTURE.md`、`docs/DATABASE.md`、`docs/API.md`、`docs/openapi.yaml`。

### 第 4 章 系统实现（建议 12–18 页）

按“问题 → 方案 → 代码 → 效果”组织，避免大段贴代码。

- 4.1 工程结构与分层
  - `controller/service/mapper/entity/dto/vo/config/aspect`；
  - 统一返回与全局异常。
- 4.2 认证与安全
  - BCrypt 密码；
  - 登录成功更换 Session；
  - 登录拦截器每请求校验账号状态；
  - 401/403 统一 JSON。
- 4.3 RBAC 权限切面
  - 注解定义、切面顺序、权限码查询；
  - 为什么不能只靠前端隐藏菜单。
- 4.4 文章业务
  - 状态机集中校验；
  - 作者所有权；
  - 乐观锁更新与 409；
  - 批量操作整批校验/回滚；
  - 逻辑删除。
- 4.5 分类树
  - 递归组装；
  - 同级重名；
  - 移动防环；
  - 删除引用保护；
  - 停用分类限制新增文章。
- 4.6 高级检索与 N+1 优化
  - 动态条件；
  - 分类子树 ID 集合；
  - 日期范围；
  - 排序白名单；
  - 文章 JOIN、用户批量 IN。
- 4.7 MyBatis-Plus 特性
  - 分页插件、Lambda Wrapper、逻辑删除、自动填充、乐观锁；
  - `MyMetaObjectHandler` 更新填充的注意点。
- 4.8 操作日志
  - `@Log` 切面；
  - 参数序列化与截断；
  - 密码脱敏；
  - 日志失败不影响主流程；
  - 登录日志用户重新解析。
- 4.9 管理端页面
  - 原生 HTML/CSS/JS，无构建依赖；
  - 登录、仪表盘、文章、分类、用户、角色、日志；
  - 按权限渲染菜单；
  - 截图见 `docs/screenshots/`。

**可参考代码位置**：

| 主题 | 文件 |
| --- | --- |
| 统一响应 | `src/main/java/com/example/cms/common/ResultVO.java` |
| 全局异常 | `src/main/java/com/example/cms/exception/GlobalExceptionHandler.java` |
| 权限切面 | `src/main/java/com/example/cms/aspect/PermissionAspect.java` |
| 日志切面 | `src/main/java/com/example/cms/aspect/OperationLogAspect.java` |
| 文章业务 | `src/main/java/com/example/cms/service/impl/ArticleServiceImpl.java` |
| 文章 SQL | `src/main/resources/mapper/CmsArticleMapper.xml` |
| MP 配置 | `src/main/java/com/example/cms/config/MybatisPlusConfig.java` |
| 自动填充 | `src/main/java/com/example/cms/config/MyMetaObjectHandler.java` |
| 管理端 | `src/main/resources/static/` |

### 第 5 章 系统测试（建议 6–8 页）

- 5.1 测试策略：接口集成 + 浏览器冒烟 + 人工边界。
- 5.2 测试环境与数据隔离（H2、MockMvc、`@Transactional` 回滚）。
- 5.3 22 个用例矩阵：按认证/RBAC、文章、分类用户角色日志分组。
- 5.4 关键测试分析
  - 越权：作者审核返回 403；
  - 状态机：待审核不可编辑；
  - 乐观锁：旧版本返回 409；
  - 注入：`' OR '1'='1` 被当作普通文本；
  - 批量事务：非法状态整批回滚；
  - 日志脱敏：无明文密码。
- 5.5 浏览器验收：12 步流程 + 截图。
- 5.6 测试结论与未覆盖项（MySQL 9.5 已在本机 3306 服务完成实机验证；高并发、跨浏览器、CSRF 未覆盖）。

**可引用材料**：`docs/TESTING.md`、`target/surefire-reports/`（运行后生成）。

### 第 6 章 部署与运行（建议 3–4 页）

- 6.1 环境要求：JDK 17、Maven、可选 MySQL 9.5。
- 6.2 H2 演示部署：`mvn clean package -DskipTests`、`java -jar target/cms.war`。
- 6.3 MySQL 部署：
  - 执行 `sql/01_schema.sql`、`sql/02_data.sql`；
  - 使用 `--spring.profiles.active=mysql`；
  - 通过 `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD` 覆盖；
  - 不要把真实密码写入仓库。
- 6.4 访问入口：管理端、健康检查、H2 控制台、Druid 监控。
- 6.5 常见问题：JDK 版本、端口占用、Druid 监控、MySQL 时区/字符集。

**可引用材料**：README 第 3、12 节。

### 第 7 章 总结与展望（建议 2–3 页）

- 7.1 工作总结：功能、权限、状态机、性能优化、测试与文档。
- 7.2 不足：
  - 单级审核；
  - 权限码每请求查库，未加缓存；
  - 正文为纯文本，无附件/富文本；
  - MySQL 9.5 已在本机 3306 服务完成实机验证，但未做多环境/高并发部署验证；
  - 无 CSRF Token 与高并发压测。
- 7.3 展望：JWT/OAuth2、Redis 权限缓存、审核链、对象存储、Elasticsearch、异步日志、Docker 部署。

### 参考文献（示例方向）

- Spring Boot 官方文档、Spring Framework 参考文档；
- MyBatis-Plus 官方文档；
- MySQL 9.5 参考手册与 Connector/J 9.5 文档；
- OWASP Top 10、OWASP Password Storage Cheat Sheet；
- RBAC 相关论文或 NIST 标准；
- 软件工程、数据库系统概论课程教材。

> 按学校要求的引用格式（GB/T 7714 等）补全，确保正文有对应引用。

### 附录

- 附录 A：数据库建表脚本（`sql/01_schema.sql`）；
- 附录 B：初始化数据（`sql/02_data.sql`）；
- 附录 C：API 摘要（`docs/API.md`）或 OpenAPI 文件；
- 附录 D：测试用例矩阵（`docs/TESTING.md`）；
- 附录 E：AI 使用声明与交互记录（`docs/AI_USAGE.md`）；
- 附录 F：运行截图（`docs/screenshots/`）；
- 附录 G：核心代码片段（权限切面、状态机、乐观锁、日志脱敏）。

## 3. 图表清单

| 编号 | 图名 | 来源 | 建议形式 |
| --- | --- | --- | --- |
| 图 2-1 | 系统用例图 | 按第 2 章角色绘制 | Mermaid / draw.io |
| 图 3-1 | 系统总体架构图 | `docs/diagrams/architecture.drawio`；`docs/ARCHITECTURE.md` 第 1 节 | draw.io / Mermaid flowchart |
| 图 3-2 | 请求处理链路时序图 | `docs/ARCHITECTURE.md` 第 2 节 | Mermaid sequence |
| 图 3-3 | RBAC 数据模型 E-R 图 | `docs/ARCHITECTURE.md` 3.1 | Mermaid erDiagram |
| 图 3-4 | 完整数据库 E-R 图 | `docs/diagrams/er.drawio`；`docs/DATABASE.md` 第 1 节 | draw.io / Mermaid erDiagram |
| 图 3-5 | 登录与会话时序图 | `docs/ARCHITECTURE.md` 3.3 | Mermaid sequence |
| 图 3-6 | 文章状态机图 | `docs/diagrams/article-workflow.drawio`；`docs/ARCHITECTURE.md` 第 4 节 | draw.io / Mermaid stateDiagram |
| 图 3-7 | 操作日志切面时序图 | `docs/ARCHITECTURE.md` 第 7 节 | Mermaid sequence |
| 图 4-1~4-9 | 管理端页面截图 | `docs/screenshots/` | PNG |
| 表 2-1 | 角色与权限矩阵 | README / API.md | 表格 |
| 表 3-1 | 数据表清单 | `docs/DATABASE.md` | 表格 |
| 表 3-2 | 文章状态迁移表 | `docs/ARCHITECTURE.md` | 表格 |
| 表 5-1 | 22 个测试用例矩阵 | `docs/TESTING.md` | 表格 |

> Mermaid 图可在 Typora、VS Code Markdown Preview、GitHub 或 mermaid.live 渲染后导出 PNG/SVG 插入 Word。需要二次编辑时，直接打开 `docs/diagrams/` 中对应的 `.drawio` 源文件，导出 300 dpi PNG 或 SVG，使用方式见 `docs/diagrams/README.md`。

## 4. 评分点映射

| 评分维度 | 本项目证据 | 展示位置 |
| --- | --- | --- |
| 需求理解与功能完整性 | 文章全生命周期、分类、用户、RBAC、日志、检索 | 第 1、2 章；README |
| 架构与分层 | Controller–Service–Mapper、DTO/VO、统一响应、全局异常 | 第 3、4 章；`docs/ARCHITECTURE.md` |
| 数据库设计 | 8 表、E-R、外键、唯一键、索引、逻辑删除 | 第 3 章；`docs/DATABASE.md`；`sql/` |
| MyBatis-Plus 应用 | 分页、Lambda、逻辑删除、自动填充、乐观锁、自定义 SQL | 第 4.7 节；配置与 XML |
| 权限与安全 | BCrypt、Session 防固定、AOP 权限、校验、注入防护、脱敏 | 第 4.2–4.3、4.8 节；测试 1–9、22 |
| 业务复杂度 | 六状态状态机、所有权、批量事务、分类防环 | 第 3.4、4.4–4.5 节；测试 10–19 |
| 性能意识 | JOIN 消除 N+1、批量 IN、索引、分页上限 | 第 4.6 节；测试 17 |
| 测试质量 | 22 个集成测试、边界/空值/注入/越权、浏览器验收 | 第 5 章；`docs/TESTING.md` |
| 工程规范 | 分层命名、Javadoc、配置分环境、README、OpenAPI | 全仓库 |
| 文档与表达 | 架构/E-R/状态图、API、测试、报告提纲 | `docs/` |
| AI 使用规范 | 声明、交互记录、人工核验、边界说明 | `docs/AI_USAGE.md` |
| 答辩演示 | 10 分钟脚本、账号分工、异常场景 | 本文件第 5 节 |

## 5. 答辩演示脚本（10 分钟）

| 时间 | 内容 | 操作/话术 |
| ---: | --- | --- |
| 0:00–0:40 | 开场 | 题目、技术栈、解决“创作—审核—发布—审计”闭环。 |
| 0:40–1:40 | 架构 | 打开 `docs/ARCHITECTURE.md`，讲分层与请求链路。 |
| 1:40–2:40 | 数据库与 RBAC | 展示 E-R 图、5 张权限相关表、权限码实时校验。 |
| 2:40–4:40 | 作者流程 | `author` 登录，新建草稿、提交审核；展示待审核不可编辑。 |
| 4:40–6:20 | 编辑流程 | `editor` 登录，审核通过、发布、下架；展示驳回必须填意见。 |
| 6:20–7:20 | 越权演示 | 用作者 Cookie 调审核接口，返回 403；说明前端隐藏不是安全边界。 |
| 7:20–8:20 | 高级检索 | 分类子树、状态、日期、排序、分页；说明 JOIN 避免 N+1。 |
| 8:20–9:20 | 日志与安全 | 打开日志详情，指出 `******`；说明 BCrypt、预编译、白名单排序。 |
| 9:20–10:00 | 测试与总结 | 展示 22 个测试通过、浏览器验收截图；说明不足与展望。 |

### 答辩常见问题准备

1. **为什么用 Spring Boot 2.7 而不是 3.x？** 任务书要求 Spring 5.x；Boot 2.7.18 对应 Spring 5.3.31，兼容 `javax.validation` 和现有生态。
2. **权限为什么不用 Spring Security？** 课程重点是理解 RBAC 与 AOP；自研轻量切面便于展示权限码查库与拦截顺序，生产可替换为 Spring Security。
3. **AOP 和拦截器有什么区别？** 拦截器负责登录态，AOP 负责方法级权限与日志；两者职责分离。
4. **如何防 SQL 注入？** MyBatis `#{}` 预编译；排序字段白名单映射；LIKE 参数绑定。
5. **乐观锁怎么实现？** `@Version` + `OptimisticLockerInnerInterceptor`，更新带版本条件，影响行数为 0 返回 409。
6. **N+1 怎么解决？** 文章列表 JOIN 分类/作者；用户列表先分页再批量 IN 查角色。
7. **批量操作为什么不会部分成功？** 同一事务内先整体校验，再逐条更新，异常整批回滚。
8. **分类如何防环？** 移动前检查目标父节点是否为自己或子孙。
9. **日志失败会影响业务吗？** 切面捕获日志写入异常，仅 WARN，不影响主流程。
10. **密码如何存储和展示？** BCrypt 密文入库；接口不返回密码；日志脱敏。
11. **为什么用逻辑删除？** 保留审计与关联数据；MyBatis-Plus 自动过滤。
12. **MySQL 验证到什么程度？** 已在本机 3306 的 MySQL 9.5.0 服务完成建表、初始化、应用启动、登录/文章接口验证和浏览器全流程冒烟；部署到其他环境时通过 `DB_PASSWORD` 等环境变量注入实际凭据即可。

## 6. 写作与排版建议

- 正文以“设计决策 + 证据”为主，代码只保留关键 10–30 行；
- 每张图在正文中先引出再解释，图题放在图下方，表题放在表上方；
- 测试章节给出“输入、预期、实际、结论”，不要只贴通过截图；
- 所有截图使用统一分辨率，隐藏真实密码与个人信息；
- AI 生成内容按学校要求标注，保留人工修改与验证记录；
- 参考文献与正文引用一一对应；
- 最后检查目录、页码、图表编号、错别字和代码缩进。
