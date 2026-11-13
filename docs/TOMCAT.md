# 本地 Tomcat 部署与排查

本文记录将 Atlas CMS 部署到本机 Tomcat 时的常见问题、根因与已验证的解决方式。

## 1. 先确认版本：只能用 Tomcat 9.x

| 项目 | 版本 |
| --- | --- |
| Spring Boot | 2.7.18（Spring Framework 5.3，`javax.servlet`） |
| 外部容器 | Apache Tomcat **9.0.x** |
| JDK | **17** |

Tomcat 10 / 11 实现了 Jakarta EE 9+，Servlet API 包名从 `javax.servlet.*` 改为 `jakarta.servlet.*`。把本项目的 WAR 部署到 Tomcat 10/11 时，Spring Boot 2.7 的监听器、过滤器和 `ServletInitializer` 都无法加载，通常表现为：

- 应用 context 启动失败 / WAR 被标记为 failed；
- `java.lang.ClassNotFoundException: javax.servlet.Filter`、`javax.servlet.ServletContext` 等；
- 页面 404，Tomcat 日志里没有 Spring Boot 启动横幅。

需要 Tomcat 10+ 时，应将项目迁移到 Spring Boot 3.x（`jakarta.*`、Java 17+），而不是只改 Tomcat 配置。

## 2. 本次实际排查到的四个问题

### 2.1 IDEA 里配置了 WAR，但项目只打 JAR

初始 `pom.xml` 为 `<packaging>jar</packaging>`，而 IDEA 的 Tomcat Run Configuration 部署的是 `mybatis_demo_cms:war exploded`。由于没有 web facet / artifact，Tomcat 启动日志只有：

```text
HostConfig.deployDirectory 把web 应用程序部署到目录 [.../webapps/manager]
```

没有任何 CMS context 的部署记录，访问地址自然 404。

**修复**：

- `pom.xml` 改为 `<packaging>war</packaging>`；
- 将 `spring-boot-starter-tomcat` 设为 `provided`，避免与外部 Tomcat 冲突；
- 新增 `ServletInitializer extends SpringBootServletInitializer`，让 Tomcat 能引导 Spring Boot。

### 2.2 context path 与前端绝对路径冲突

IDEA 默认把 WAR exploded 部署到 `/mybatis_demo_cms_war_exploded`，而原前端写死了：

```html
<link rel="stylesheet" href="/css/app.css">
<script src="/js/app.js"></script>
```

```js
fetch('/api/auth/login')
```

这些请求会落到 Tomcat 根路径，而不是应用的 context path，导致登录页无样式、按钮无响应、接口 404。

**修复**：

- HTML 中的 CSS / JS / favicon 改为相对路径；
- `app.js` 根据脚本自身 URL 计算应用根路径：

```js
const APP_BASE = new URL('../', new URL(script.src, location.href)).pathname; // /cms/
fetch(APP_BASE + 'api/health');
```

因此 `/`、`/cms`、`/mybatis_demo_cms_war_exploded` 均可正常使用。

### 2.3 Tomcat 使用了 JDK 24

日志显示 Tomcat 由 `E:\Java\jdk-24.0.2` 启动。Spring Boot 2.7 官方支持到 Java 17（兼容范围有限），JDK 24 下可能出现 CGLIB/ASM、反射相关的启动异常。

**修复**：IDEA → Tomcat Run Configuration → `JRE` 选择 **17**（或设置 `JAVA_HOME=E:\Java\jdk-17.0.12`）。

### 2.4 两个 Tomcat 都占用 8080

日志中先后启动了 Tomcat 11.0.18 与 Tomcat 9.0.98，均使用 8080。切换版本前请确认旧进程已经停止：

```bat
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

## 3. 推荐的 IDEA 配置

1. `Run/Debug Configurations` → `+` → `Tomcat Server` → `Local`
2. **Application server**：Tomcat 9.0.x
3. **JRE**：17
4. **Deployment** → `+` → Artifact → `cms:war exploded`
5. **Application context**：`/cms`（与前端自适应的 context path 配合）
6. 启动后访问：`http://localhost:8080/cms/`
7. 确认 `target/cms/WEB-INF/classes/com/example/cms/ServletInitializer.class` 存在

如果 IDEA 仍提示找不到 artifact：

```text
File → Project Structure → Artifacts → + → Web Application: Exploded → From Modules
```

或直接使用 Maven 构建的 WAR。

## 4. 手工部署（最稳妥）

```bash
mvn clean package -DskipTests
cp target/cms.war /path/to/apache-tomcat-9.0.x/webapps/
# 用 JDK 17 启动
export JAVA_HOME=/path/to/jdk-17
$CATALINA_HOME/bin/startup.sh
```

访问 `http://localhost:8080/cms/`。

- 想部署到根路径：把 `cms.war` 重命名为 `ROOT.war`（先移除 `webapps/ROOT`）。
- 想修改 context path：文件名即 context path，例如 `atlas.war` → `/atlas/`。

## 5. MySQL profile 下的 Tomcat

Tomcat 不读取 `application.yml` 里的命令行参数，使用环境变量：

```text
SPRING_PROFILES_ACTIVE=mysql
DB_HOST=localhost
DB_PORT=3306
DB_NAME=cms_db
DB_USERNAME=root
DB_PASSWORD=你的密码
```

Windows 可在 `%CATALINA_HOME%\bin\setenv.bat` 中设置：

```bat
set "SPRING_PROFILES_ACTIVE=mysql"
set "DB_HOST=localhost"
set "DB_PORT=3306"
set "DB_NAME=cms_db"
set "DB_USERNAME=root"
set "DB_PASSWORD=你的密码"
set "JAVA_HOME=E:\Java\jdk-17.0.12"
```

IDEA 中则在 Tomcat Run Configuration 的 `Startup/Connection` → `Environment variables` 中配置。

## 6. 部署后验收清单

- [ ] `GET /cms/api/health` 返回 `{"code":200,...,"status":"UP"}`
- [ ] 登录页样式正常，控制台无 `404 /css`、`404 /js`、`404 /api`
- [ ] `admin / admin123` 登录成功，侧边栏显示 6 个模块
- [ ] 文章列表能加载 6 条种子数据，分类下拉为树形缩进
- [ ] 分类、用户、角色、日志页面均可切换
- [ ] 角色权限修改后，重新登录生效
- [ ] Tomcat 日志出现 `Started CmsApplication` 且无 `javax.servlet` / `jakarta.servlet` 异常

## 7. 常见错误对照表

| 现象 | 根因 | 处理 |
| --- | --- | --- |
| 访问 `/cms/` 404，日志只有 manager 部署 | WAR artifact 未构建/未部署 | 检查 `packaging=war`、`ServletInitializer`、IDEA Deployment |
| 登录页无样式、按钮无反应 | 静态资源绝对路径忽略 context path | 使用相对路径 + `APP_BASE`（已修复） |
| 接口全部 404，但页面能打开 | JS 中 `/api` 写死 | 确认使用的是当前版本 `app.js` |
| 启动报 `ClassNotFoundException: javax.servlet.*` | Tomcat 10/11 | 换 Tomcat 9.0.x |
| 启动报 `Unsupported class file major version` | JDK 版本过高/编译版本不匹配 | Tomcat 与 Maven 均使用 JDK 17 |
| 8080 端口占用 | 旧 Tomcat 未退出 | `netstat -ano \| findstr :8080` 后结束进程 |
| 中文乱码 | 终端/数据库编码 | MySQL 导入使用 `--default-character-set=utf8mb4`；Tomcat `-Dfile.encoding=UTF-8` |
