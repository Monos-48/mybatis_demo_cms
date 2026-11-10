# 测试与验收说明

本项目采用“**接口集成测试 + 浏览器端到端冒烟 + 人工边界检查**”三层验证。接口测试基于 JUnit 5、Spring Boot Test、MockMvc 与 H2，覆盖认证、RBAC、文章状态机、参数边界、注入防护、分类、用户、角色和日志脱敏；浏览器冒烟验证真实页面上的完整业务闭环。

## 1. 测试环境

| 项目 | 值 |
| --- | --- |
| 操作系统 | Windows 11 |
| JDK | 17（`JAVA_HOME` 指向 JDK 17） |
| 构建工具 | Maven 3.8+ |
| 应用框架 | Spring Boot 2.7.18 / Spring 5.3.31 |
| 测试数据库 | H2 2.x（MySQL 兼容模式，内存库） |
| MySQL 验收 | MySQL Community Server 9.5.0 + Connector/J 9.5.0 |
| Web 测试 | MockMvc |
| 浏览器验收 | Google Chrome + Playwright |
| 测试配置 | `src/test/resources/application-test.yml` |

> 必须使用 JDK 17。若本机默认 Maven 使用更高版本 JDK，请显式设置 `JAVA_HOME`。

### 1.1 运行全部测试

```bash
# Git Bash 示例；Windows 路径按本机安装位置调整
export JAVA_HOME='E:/Java/jdk-17.0.12'
export PATH="$JAVA_HOME/bin:$PATH"
mvn -q test
```

PowerShell：

```powershell
$env:JAVA_HOME = 'E:\Java\jdk-17.0.12'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test
```

### 1.2 运行单个测试类

```bash
mvn -q -Dtest=ArticleWorkflowIntegrationTest test
mvn -q -Dtest=AuthRbacIntegrationTest test
mvn -q -Dtest=CategoryUserRoleLogIntegrationTest test
```

### 1.3 打包并跳过测试

```bash
mvn clean package -DskipTests
java -jar target/cms.jar
```

### 1.4 测试隔离设计

- `BaseIntegrationTest` 使用 `@SpringBootTest + @AutoConfigureMockMvc`；
- `@ActiveProfiles({"h2", "test"})` 启用 H2 与测试覆盖配置；
- 每个测试方法标注 `@Transactional`，测试结束自动回滚，避免用例之间互相污染；
- `application-test.yml` 关闭 Druid 的 `web-stat-filter` 与 `stat-view-servlet`。MockMvc 不经过真实 Servlet 容器初始化，Druid Web 统计会触发 `WebAppStat.beforeInvoke()` 空指针，关闭后不影响连接池与 SQL 功能测试；
- 测试依赖 H2 种子数据（5 个账号、7 个分类、6 篇文章、4 条日志），断言与 `data-h2.sql` 保持一致。

### 1.5 MySQL 9.5 实机兼容验证

为避免使用本机 3306 服务的未知 root 凭据，使用同一台机器上的 MySQL 9.5.0 二进制初始化了一个**隔离测试实例**（端口 3307，数据目录位于 `target/mysql95-test`），不影响现有 MySQL95 服务：

```bash
# 初始化隔离实例（无默认密码，仅本机测试）
mysqld --no-defaults --initialize-insecure \
  --basedir="C:/Program Files/MySQL/MySQL Server 9.5" \
  --datadir="target/mysql95-test/data"

# 启动隔离实例
mysqld --no-defaults --port=3307 --mysqlx-port=33070 \
  --datadir="target/mysql95-test/data"

# 导入 MySQL 9.5 脚本
mysql --no-defaults -h127.0.0.1 -P3307 -uroot < sql/01_schema.sql
mysql --no-defaults -h127.0.0.1 -P3307 -uroot < sql/02_data.sql

# 使用 MySQL profile 启动应用
SPRING_PROFILES_ACTIVE=mysql DB_HOST=127.0.0.1 DB_PORT=3307 \
DB_NAME=cms_db DB_USERNAME=root DB_PASSWORD='' java -jar target/cms.jar
```

验证结果：

| 项目 | 结果 |
| --- | --- |
| 服务器版本 | MySQL 9.5.0 Community Server，端口 3307 |
| 建表 | 成功，8 张表 |
| 种子数据 | 5 个用户、6 篇文章、13 个权限点 |
| Connector/J | 9.5.0，`com.mysql.cj.jdbc.Driver` 正常加载 |
| 应用启动 | `Started CmsApplication`，`/api/health` 返回 200 |
| 浏览器冒烟 | `UI_SMOKE_OK`，完整文章审核发布流程通过 |
| 自动化测试 | 22 个测试通过（仍使用 H2，Connector/J 9.5 同在类路径） |

> 时区说明：连接串使用 `connectionTimeZone=Asia/Shanghai`，不强制写入会话时区。这样即使 MySQL 实例未加载时区表（新的最小化实例常见），也能连接；应用层 Jackson 仍统一 GMT+8。若生产库已加载时区表，也可额外设置 `forceConnectionTimeZoneToSession=true` 让会话时区与客户端一致。

## 2. 自动化测试结果

执行日期：2026-11-11。命令：`mvn -q test`（JDK 17）。结果：**22 个测试全部通过，BUILD SUCCESS**。

| 测试类 | 用例数 | 关注点 |
| --- | ---: | --- |
| `AuthRbacIntegrationTest` | 9 | 匿名拦截、登录、RBAC、注册审批、校验、退出 |
| `ArticleWorkflowIntegrationTest` | 8 | 状态机、审核元数据、乐观锁、边界、注入、归属、批量、检索 |
| `CategoryUserRoleLogIntegrationTest` | 5 | 分类树/防环/删除保护、用户、角色权限、日志脱敏 |
| **合计** | **22** | |

## 3. 用例矩阵

### 3.1 认证与 RBAC（9 个）

| # | 测试方法 | 场景与输入 | 预期结果 |
| ---: | --- | --- | --- |
| 1 | `anonymousRequestIsRejectedWithUnifiedBody` | 未登录访问 `GET /api/articles` | 401，统一响应体，提示包含“登录” |
| 2 | `adminLoginReturnsRolesAndPermissions` | 管理员登录后访问 `/api/auth/me` | 200；角色含 `ADMIN`；13 个权限点，含审核与用户管理 |
| 3 | `wrongPasswordIsUnauthorized` | 正确用户名 + 错误密码 | 401，“用户名或密码错误” |
| 4 | `pendingUserCannotLogin` | 待审批账号 `pending` 登录 | 403，提示等待审批 |
| 5 | `authorCannotAccessAdminInterfaces` | 作者访问用户、日志、审核接口 | 三处均 403，AOP 在业务前拦截 |
| 6 | `viewerIsReadOnly` | 只读用户查列表、尝试新建文章 | 查询 200；新建 403 |
| 7 | `registeredUserIsPendingUntilApproved` | 注册新用户；重复注册；登录 | 状态 2、默认 AUTHOR；重复 409；登录 403 |
| 8 | `registrationValidationRejectsWeakInput` | 非法用户名/弱密码/过短昵称/非法邮箱 | 400，字段级中文提示同时返回 |
| 9 | `logoutInvalidatesSession` | 登录后退出，再访问受保护接口 | 退出 200；随后 401 |

### 3.2 文章状态机与高级检索（8 个）

| # | 测试方法 | 场景与输入 | 预期结果 |
| ---: | --- | --- | --- |
| 10 | `fullWorkflowAndAuditMetadata` | 草稿→编辑→提交→越权审核→编辑审核→发布→下架→重新发布→错误状态审核 | 状态 0→1→2→4→5→4；版本递增；审核人/意见/发布时间正确；非法状态 409 |
| 11 | `rejectedAuditRequiresRemark` | 驳回不填意见；补充意见后驳回 | 第一次 400；第二次 200，状态 3 且意见落库 |
| 12 | `optimisticLockRejectsStaleVersion` | 用 `version=99` 修改版本 0 的文章 | 409，提示刷新重试；数据库内容与版本不变 |
| 13 | `validationAndBoundaryInput` | 标题 1 字、正文 5 字；停用分类；畸形 JSON；非法排序；不存在 ID | 依次 400/400/400/400/404，无堆栈泄露 |
| 14 | `sqlInjectionPayloadIsTreatedAsPlainText` | 标题包含 `' OR '1'='1`，再按该标题检索 | 200；仅命中该文章，注入串按普通文本处理 |
| 15 | `ownershipAndDeletionRules` | 作者删他人文章；作者删已发布文章；编辑删任意文章 | 403；409；200 且详情变 404（逻辑删除） |
| 16 | `batchPublishAndOffline` | 批量发布、批量下架；含非法状态；空数组 | 前两步 200；非法状态 409 整批回滚；空数组 400 |
| 17 | `advancedSearchSupportsSubtreeStatusDateAndSort` | 分类子树/仅本级、状态、日期、浏览量排序、分页 | 数量与顺序符合种子数据；`pages≥3`；每页 2 条 |

### 3.3 分类、用户、角色与日志（5 个）

| # | 测试方法 | 场景与输入 | 预期结果 |
| ---: | --- | --- | --- |
| 18 | `categoryTreeSupportsEnabledFilter` | 全量树、仅启用树、不存在分类 | 3 个顶级且“技术资讯”有 3 个子级；仅启用 2 个顶级；404 |
| 19 | `categoryCrudGuardsSiblingNamesCyclesAndReferences` | 同级重名、移动到子孙、删除有子级/有文章分类、非法状态值 | 409/400/400/400/400，错误消息可读 |
| 20 | `userManagementAssemblesRolesAndProtectsCurrentAccount` | 检索用户；创建作者；改角色；重置密码；禁用；操作自己；非法角色 | 角色正确组装；新密码可登录；禁用后 403；自我禁用/改角色/删除均 400 |
| 21 | `roleAndPermissionManagement` | 查角色/权限；新建角色；重复编码；分配权限；删除；删 ADMIN | 4 个内置角色、13 个权限；重复 409；分配/删除 200；ADMIN 删除 400 |
| 22 | `operationLogsAreFilterableAndMaskPasswords` | 按模块/成功状态/用户名筛选；检查登录参数 | 筛选正确；无明文 `admin123`，出现 `******` |

## 4. 浏览器端到端验收

使用真实 Chrome，通过 Playwright 驱动运行中的 `target/cms.jar`。H2 每次启动重置，验收从干净数据开始。

### 4.1 验收脚本步骤

| 步骤 | 操作 | 验收点 | 截图 |
| ---: | --- | --- | --- |
| 1 | 打开 `http://localhost:8080/` | 登录页正常，演示账号可一键填充 | `ui-01-login.png` |
| 2 | `admin/admin123` 登录 | 仪表盘统计、全功能菜单可见 | `ui-02-dashboard-admin.png` |
| 3 | 文章管理 → 新建草稿 | 新文章状态“草稿”，列表出现且可检索 | `ui-03-articles-admin.png` |
| 4 | 作者账号提交该草稿 | 状态变为“待审核”，作者编辑按钮受限 | — |
| 5 | `editor/editor123` 登录 → 审核通过 | 状态“审核通过”，审核意见保存 | — |
| 6 | 发布文章 → 打开详情 | 状态“已发布”，发布时间与正文正确 | `ui-04-article-detail.png` |
| 7 | 分类管理 | 多级树展开，启停/编辑入口可用 | `ui-05-categories-editor.png` |
| 8 | 用户管理 | 角色标签、状态、审批/禁用操作可见 | `ui-06-users-admin.png` |
| 9 | 角色权限 | 权限矩阵按模块分组，可提交分配 | `ui-07-roles-admin.png` |
| 10 | 操作日志 | 可按模块筛选，详情参数密码显示 `******` | `ui-08-logs-admin.png`、`ui-09-log-detail.png` |
| 11 | 切到 `editor` 复核菜单 | 不出现“用户管理/角色权限/操作日志”入口 | 同 `ui-05` |
| 12 | 退出后直接访问受保护接口 | 返回 401 JSON，不泄露页面数据 | — |

### 4.2 已通过的冒烟记录

本地最终冒烟输出（功能项）：

```text
login page OK
admin dashboard OK
article draft created OK
submit for review OK, articleId=7
editor audit OK
publish OK
article detail OK
category tree OK, editor nav isolation OK
user management OK
role matrix OK
operation logs OK
log masking OK
```

说明：

- 页面首次加载调用 `/api/auth/me` 返回 401 属于未登录预期行为，浏览器控制台会出现一条 401 网络记录，不影响功能；
- 已添加 `/favicon.svg` 消除 favicon 404；
- 验收截图位于 [`screenshots/`](screenshots/)，由上述真实运行页面生成。

## 5. 人工边界核对清单

自动化未覆盖或需人工观察的项目：

- [ ] 登录页回车提交、错误提示与焦点恢复；
- [ ] 表格为空时的空状态文案；
- [ ] 分类树较深时的缩进与滚动；
- [ ] 文章正文超长时的编辑区滚动；
- [ ] 浏览器前进/后退不会绕过登录（接口仍返回 401）；
- [ ] 连续点击提交按钮不会产生重复文章（前端按钮禁用）；
- [ ] 移动端窄屏下表格横向滚动、侧栏折叠；
- [ ] MySQL profile 下的中文、emoji、时区显示；
- [ ] Druid 监控页仅本机可访问，生产环境应关闭或改密；
- [ ] 使用 `curl` 验证 401/403 响应体确实是 JSON，而非登录页 HTML。

## 6. 覆盖与需求对应

| 任务书要求 | 对应验证 |
| --- | --- |
| 登录、密码加密、会话安全 | 用例 1–4、7、9；日志脱敏用例 22 |
| 接口级 RBAC | 用例 5、6、7、10、20、21 |
| 文章发布与审核流程 | 用例 10、11、16 |
| 多级分类 | 用例 18、19 |
| 高级检索 | 用例 13、14、17 |
| 参数校验与异常处理 | 用例 8、13、19、20、21 |
| 乐观锁与并发冲突 | 用例 12 |
| 批量操作事务 | 用例 16 |
| 操作日志与脱敏 | 用例 22；浏览器步骤 10 |
| 空值/边界 | 用例 8、13、16、19、20 |
| 真实页面可用性 | 浏览器验收步骤 1–12 |

## 7. 未覆盖与风险说明

1. **MySQL 运行验证**：已在隔离的 MySQL 9.5.0 实例上完成建表、初始化、应用启动和浏览器全流程冒烟（见 1.5 节）。本机 3306 的 MySQL95 服务凭据未知，未进行猜测或暴力尝试；正式部署时只需用实际的 `DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD` 连接该服务即可。
2. **并发压力**：仅验证乐观锁冲突，不做高并发压测；生产需补充 JMeter/Gatling 与连接池容量评估。
3. **浏览器兼容性**：主要验证 Chrome；Firefox/Safari 未做像素级兼容测试。
4. **CSRF**：当前使用 Session Cookie 且未引入 CSRF Token，课程内网演示可接受；若公网部署，应增加 SameSite/CSRF 防护或改用 Token 认证。
5. **前端自动化**：Playwright 冒烟脚本用于本地验收，不作为 Maven 测试依赖，避免给构建引入 Node 环境要求。
