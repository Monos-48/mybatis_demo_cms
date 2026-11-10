# API 接口文档

- Base URL：`http://localhost:8080`
- 数据格式：`application/json; charset=UTF-8`
- 认证方式：Session Cookie（登录成功后由服务端下发 `JSESSIONID`，浏览器自动携带）
- 机器可读规范：[`openapi.yaml`](openapi.yaml)，可直接导入 ApiFox / Swagger Editor

## 1. 统一响应结构

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2026-11-11 10:00:00"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务状态码，与 HTTP 状态码保持一致 |
| `message` | string | 中文提示，可直接展示给用户 |
| `data` | any | 业务数据；无返回时为 `null` |
| `timestamp` | string | 服务端时间，`yyyy-MM-dd HH:mm:ss` |

分页数据统一为：

```json
{
  "records": [],
  "total": 42,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 5
}
```

### 状态码

| HTTP / code | 含义 | 典型场景 |
| --- | --- | --- |
| 200 | 成功 | 查询、增删改成功 |
| 400 | 参数错误/业务校验失败 | JSR-303 校验、分类停用、驳回未填意见 |
| 401 | 未登录/登录失效 | Session 缺失、密码错误 |
| 403 | 无权限/账号不可用 | 缺少权限码、账号待审批/禁用 |
| 404 | 资源不存在 | 文章/分类/用户不存在 |
| 405 | 方法不支持 | 错误的 HTTP Method |
| 409 | 数据冲突 | 重名、状态机不允许、乐观锁冲突 |
| 500 | 系统错误 | 未捕获异常（不返回堆栈） |

错误示例：

```json
{
  "code": 403,
  "message": "没有操作权限：article:audit",
  "data": null,
  "timestamp": "2026-11-11 10:00:00"
}
```

## 2. 认证接口

### 2.1 登录

`POST /api/auth/login`，公开。

请求：

```json
{
  "username": "admin",
  "password": "admin123"
}
```

响应 `data`：

```json
{
  "id": 1,
  "username": "admin",
  "nickname": "系统管理员",
  "email": "admin@cms.local",
  "roles": ["ADMIN"],
  "permissions": [
    "article:list", "article:create", "article:edit", "article:delete",
    "article:submit", "article:audit", "article:publish", "article:offline",
    "category:list", "category:manage", "user:manage", "role:manage", "log:view"
  ]
}
```

错误：密码错误 401；账号待审批/禁用 403。登录成功后服务端会更换 Session，防止会话固定。

### 2.2 注册

`POST /api/auth/register`，公开。

```json
{
  "username": "new_author",
  "password": "author123",
  "nickname": "新作者",
  "email": "new@example.com"
}
```

注册用户状态为 `2 待审批`，默认预分配 AUTHOR 角色，管理员启用后才能登录。

### 2.3 当前用户

`GET /api/auth/me`，需登录。返回与登录一致的 `LoginVO`，角色与权限实时从数据库加载。

### 2.4 退出

`POST /api/auth/logout`，需登录。清除 Session。

### 2.5 健康检查

`GET /api/health`，公开。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "status": "UP",
    "application": "mybatis_demo_cms",
    "time": "2026-11-11 10:00:00"
  },
  "timestamp": "2026-11-11 10:00:00"
}
```

## 3. 文章接口

权限：除列表/详情为 `article:list` 外，写操作分别对应 `article:create/edit/delete/submit/audit/publish/offline`。

### 3.1 分页检索

`GET /api/articles`

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNum` | int | 否 | 默认 1，最小 1 |
| `pageSize` | int | 否 | 默认 10，范围 1–100 |
| `title` | string | 否 | 标题模糊搜索，≤100 字 |
| `categoryId` | long | 否 | 分类过滤 |
| `includeChildren` | boolean | 否 | 是否包含子孙分类，默认 true |
| `status` | int | 否 | 0–5 |
| `authorId` | long | 否 | 作者过滤 |
| `startDate` | date | 否 | 创建日期起，含当天 |
| `endDate` | date | 否 | 创建日期止，含当天 |
| `sortField` | string | 否 | `createTime/updateTime/publishTime/viewCount/title` |
| `sortOrder` | string | 否 | `asc/desc`，默认 desc |

示例：

```http
GET /api/articles?pageNum=1&pageSize=10&status=4&categoryId=1&sortField=publishTime&sortOrder=desc
```

响应 `data.records[]` 为 `ArticleVO`，包含 `id/title/summary/categoryId/categoryName/authorId/authorName/status/statusText/auditRemark/publishTime/viewCount/version/createTime/updateTime`。分类名与作者名由 JOIN 一次带出。

### 3.2 详情

`GET /api/articles/{id}`，返回 `ArticleDetailVO`，在列表字段基础上增加 `content/auditBy/auditorName/auditTime`。

### 3.3 新建文章

`POST /api/articles`

```json
{
  "title": "企业级 CMS 的权限设计",
  "summary": "接口级权限与数据归属的双重边界",
  "content": "正文至少十个字符……",
  "categoryId": 4,
  "submitForReview": false
}
```

`submitForReview=false` 保存草稿；为 `true` 时直接进入待审核。校验：标题 2–100 字、正文 10–100000 字、分类必须存在且启用。

响应 `data` 为文章 ID（数字）。

### 3.4 编辑文章

`PUT /api/articles/{id}`

```json
{
  "title": "企业级 CMS 的权限设计（修订）",
  "summary": "接口级权限与数据归属的双重边界",
  "content": "修订后的正文内容……",
  "categoryId": 4,
  "version": 0,
  "submitForReview": false
}
```

`version` 必须传当前版本号。仅作者本人或 EDITOR/ADMIN 可编辑，且仅草稿/已驳回可编辑；版本不一致返回 409。

### 3.5 删除文章

`DELETE /api/articles/{id}`

- 作者只能删除自己的草稿/已驳回文章；
- EDITOR/ADMIN 可删除任意文章；
- 逻辑删除，历史数据保留。

### 3.6 提交审核

`POST /api/articles/{id}/submit`，无请求体。仅草稿/已驳回可提交；提交后作者不可再编辑。

### 3.7 审核

`POST /api/articles/{id}/audit`，权限 `article:audit`。

```json
{
  "approved": false,
  "remark": "选题与栏目不符，请补充数据来源"
}
```

- 仅待审核文章可审核；
- `approved=false` 时 `remark` 必填；
- 记录审核人、审核时间、审核意见。

### 3.8 发布/下架

- `POST /api/articles/{id}/publish`：仅审核通过/已下架可发布，写入 `publishTime`；
- `POST /api/articles/{id}/offline`：仅已发布可下架。

### 3.9 批量发布/下架

`POST /api/articles/batch/publish`、`POST /api/articles/batch/offline`

```json
{
  "articleIds": [1, 2, 3]
}
```

数量 1–100；批量操作先整体校验状态，再逐条更新并写入操作日志；任一状态不合法整批回滚。

## 4. 分类接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/categories/tree?enabledOnly=false` | `category:list` | 返回递归树，`children` 为子节点 |
| GET | `/api/categories/{id}` | `category:list` | 分类详情 |
| POST | `/api/categories` | `category:manage` | 新建分类 |
| PUT | `/api/categories/{id}` | `category:manage` | 编辑/移动分类 |
| PUT | `/api/categories/{id}/status` | `category:manage` | 启用/停用 |
| DELETE | `/api/categories/{id}` | `category:manage` | 删除（存在子分类或文章时拒绝） |

新增/编辑请求：

```json
{
  "name": "云原生",
  "parentId": 1,
  "sort": 5,
  "status": 1,
  "description": "容器、Kubernetes 与可观测性"
}
```

规则：同级不可重名；父分类必须存在；不能移动到自己或子孙节点下；停用分类不可挂载新文章；删除前校验引用。

分类树节点：

```json
{
  "id": 1,
  "name": "技术资讯",
  "parentId": 0,
  "sort": 1,
  "status": 1,
  "statusText": "启用",
  "description": "技术领域新闻与深度解读",
  "createTime": "2026-10-01 10:00:00",
  "children": []
}
```

## 5. 用户接口（`user:manage`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/users` | 分页检索 |
| GET | `/api/users/{id}` | 用户详情（含角色） |
| POST | `/api/users` | 管理员创建用户 |
| PUT | `/api/users/{id}/status` | 启用/禁用/审批 |
| PUT | `/api/users/{id}/roles` | 重新分配角色 |
| PUT | `/api/users/{id}/password` | 重置密码 |
| DELETE | `/api/users/{id}` | 删除用户 |

查询参数：`pageNum/pageSize/keyword/status/roleId`。`keyword` 匹配用户名或昵称，`status` 0/1/2。

创建用户：

```json
{
  "username": "content_editor",
  "password": "editor123",
  "nickname": "内容编辑",
  "email": "editor@example.com",
  "status": 1,
  "roleIds": [2]
}
```

重置密码：

```json
{ "newPassword": "newpass123" }
```

分配角色：

```json
{ "roleIds": [3, 4] }
```

保护规则：不能禁用/删除当前登录账号；不能修改当前登录账号自己的角色；角色必须存在且启用；密码 8–32 位且含字母和数字。

## 6. 角色与权限接口（`role:manage`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/roles` | 角色列表，含 `permissionIds` |
| POST | `/api/roles` | 新建角色 |
| PUT | `/api/roles/{id}` | 编辑角色 |
| DELETE | `/api/roles/{id}` | 删除角色 |
| PUT | `/api/roles/{id}/permissions` | 重新分配权限 |
| GET | `/api/permissions` | 全部权限点，用于前端分组展示 |

新建/编辑角色：

```json
{
  "roleName": "内容审核员",
  "roleCode": "REVIEWER",
  "status": 1,
  "remark": "只负责文章审核"
}
```

- 角色编码须为 2–30 位大写字母/数字/下划线且以字母开头，唯一；
- 内置 ADMIN 角色编码不可修改、角色不可删除；
- 仍绑定用户的角色不可删除；
- 权限分配使用 `{"permissionIds":[1,6,7]}`。

## 7. 操作日志接口（`log:view`）

`GET /api/logs`

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `pageNum/pageSize` | int | 分页 |
| `module` | string | 模块模糊搜索，如“文章管理” |
| `username` | string | 操作人模糊搜索 |
| `success` | int | 1 成功 / 0 失败 |
| `startDate` | date | 起始日期 |
| `endDate` | date | 结束日期 |

日志字段：`id/userId/username/module/operation/method/requestUri/requestMethod/params/ip/costTime/success/errorMsg/createTime`。`params` 中密码字段为 `******`。

## 8. 调用示例

### 8.1 curl 登录并创建草稿

```bash
# 1. 登录，保存 Cookie
curl -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"username":"author","password":"author123"}' \
  http://localhost:8080/api/auth/login

# 2. 创建草稿
curl -b cookies.txt -H 'Content-Type: application/json' \
  -d '{"title":"接口联调示例文章","summary":"API 文档示例","content":"这是一段用于接口联调演示的正文内容。","categoryId":4,"submitForReview":false}' \
  http://localhost:8080/api/articles
```

### 8.2 ApiFox 使用

1. 新建项目，导入 `docs/openapi.yaml`；
2. 环境变量 `baseUrl = http://localhost:8080`；
3. 先调用“登录”接口，ApiFox 会自动保存 Cookie；
4. 后续接口可直接选择管理员/作者等账号的登录 Cookie 运行。

## 9. 权限矩阵

| 功能 | ADMIN | EDITOR | AUTHOR | VIEWER |
| --- | --- | --- | --- | --- |
| 文章列表/详情 | ✓ | ✓ | ✓ | ✓ |
| 新建/编辑/删除/提交 | ✓ | ✓ | ✓（仅自己的草稿/驳回） | ✗ |
| 审核/发布/下架 | ✓ | ✓ | ✗ | ✗ |
| 分类查看 | ✓ | ✓ | ✓ | ✓ |
| 分类管理 | ✓ | ✓ | ✗ | ✗ |
| 用户管理 | ✓ | ✗ | ✗ | ✗ |
| 角色权限 | ✓ | ✗ | ✗ | ✗ |
| 操作日志 | ✓ | ✗ | ✗ | ✗ |

> 前端菜单会按权限隐藏，但服务端 AOP 才是最终安全边界；即使绕过页面直接请求接口，缺少权限码也会返回 403。
