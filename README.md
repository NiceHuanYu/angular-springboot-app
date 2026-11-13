# my-fullstack-app

Angular + Spring Boot 双模块项目，最终打包成**一个可执行 jar** 部署（前端静态资源打进 jar，由一个进程同时提供页面和 API）。

## 技术栈

| 层 | 版本 |
|---|---|
| Java | 25（`java.version=25`，需 JDK 25 编译） |
| Spring Boot | 4.1.2 |
| Maven | 3.10.0（通过 `mvnw` wrapper，无需本机安装 Maven） |
| Angular | 22.2（`@angular/build:application` builder） |
| Node | v24.19.0（由 `frontend-maven-plugin` 自动下载，满足 Angular 22 的 `^22.22.3 \|\| ^24.15.0 \|\| >=26`） |

## 目录结构

```
my-fullstack-app/
├── pom.xml                  # 父 pom：packaging=pom，聚合 + 版本属性
├── mvnw / mvnw.cmd / .mvn/  # 全项目共用的 Maven wrapper
├── backend/                 # Spring Boot 应用
│   ├── pom.xml
│   └── src/main/java/top/huanyu666/backend/
│       ├── BackendApplication.java
│       └── web/SpaFallbackFilter.java
└── frontend/                # Angular 应用
    ├── pom.xml              # 构建前端 + 打资源 jar
    ├── angular.json
    ├── proxy.conf.json      # 开发期 /api 代理到 8080
    └── src/
```

## 构建与运行

```powershell
# 在项目根目录执行，产物：backend/target/backend-0.0.1-SNAPSHOT.jar
.\mvnw.cmd clean package

# 运行
java -jar backend\target\backend-0.0.1-SNAPSHOT.jar
```

访问 http://localhost:8080 。

常用参数：

```powershell
# 跳过测试
.\mvnw.cmd clean package "-DskipTests"

# 跳过前端构建（要求 frontend/dist 已存在）
.\mvnw.cmd clean package "-Dskip.npm" "-Dskip.installnodenpm"
```

> PowerShell 里带点的 `-D` 参数必须加引号，否则会被拆成两个参数并报 `Unknown lifecycle phase ".npm"`。

## 开发模式

开发期**前后端分开跑**，不要用 `mvnw package` 或 `spring-boot:run`：

```powershell
# 终端 A：后端 8080（IDEA 里直接用 Application 运行 BackendApplication 更快）
.\mvnw.cmd -pl backend spring-boot:run

# 终端 B：前端 4200，带 HMR
cd frontend; npm start
```

浏览器只访问 **http://localhost:4200**，前端 API 调用写相对路径 `/api/...`，由 `proxy.conf.json` 代理到 8080。

IDEA 推荐的两个 Run Configuration：

| 配置 | 类型 | 关键字段 |
|---|---|---|
| `backend (dev)` | Application | Main class `top.huanyu666.backend.BackendApplication`，JRE 25 |
| `frontend (dev)` | npm | package.json 指向 `frontend/package.json`，Command `run`，Scripts `start` |

## 关键实现说明

### 1. 前端产物如何进 jar

`frontend/pom.xml` 分两步：

1. `frontend-maven-plugin` 在 `initialize` 阶段下载固定版本 Node，`generate-resources` 阶段执行 `npm ci` + `npm run build`；
2. `maven-resources-plugin` 在 `prepare-package` 阶段把 `dist/frontend/browser/` 复制到本模块 jar 的 `META-INF/resources/`。

`backend` 以 `runtime` scope 依赖 `frontend`，Spring Boot repackage 时该 jar 进入 `BOOT-INF/lib/frontend-*.jar`；Spring Boot 默认静态资源位置包含 `classpath:/META-INF/resources/`，因此**无需任何额外配置**即可提供页面。

### 2. SPA 深链接回退

`SpaFallbackFilter` 把「GET + 不以 `/api` 开头 + 路径不含 `.`」的请求 forward 到 `/index.html`：

- `/dashboard` 这类前端路由刷新时不会 404，由 Angular Router 渲染对应页面；
- `/api/**` 保持返回真实 JSON / 404；
- `main-*.js` 等真实文件请求正常放行。

> 开发期由 Angular dev server 自己处理回退，该 filter 只在打 jar 后生效。

### 3. 依赖版本覆盖

`pom.xml` 中通过属性覆盖 Spring Boot 4.1.2 的 BOM，取值等同官方 4.2.0 的版本组合，用于修复依赖漏洞告警：

```xml
<jackson-bom.version>3.1.8</jackson-bom.version>
<jackson-2-bom.version>2.21.8</jackson-2-bom.version>
<logback.version>1.6.6</logback.version>
<json-smart.version>2.6.1</json-smart.version>
```

`json-smart 2.6.1` 会连带把 `org.ow2.asm` 从 9.7.1 升到 9.11.1，无需单独 pin。

已知无法修复：`com.vaadin.external.google:android-json`（Maven Central 仅此一版，且为 test scope，不进入产物）。

## 常见问题

| 现象 | 原因 / 解决 |
|---|---|
| `Fatal error compiling: 不支持发行版本 25` | 命令行 `JAVA_HOME` 指向了 JDK 17。在 IDEA 里用 Maven 面板打包（用项目 SDK 25），或临时 `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.5.0.3'` |
| IDEA 提示「依赖项可传递 Insufficient Information」 | `frontend` 是 reactor 内的 `runtime` scope 模块，IDEA 无法推断传递性，属正常提示 |
| 开发时访问 8080 是 404 | 开发进程的 classpath 里没有前端产物，正常现象；开发期请访问 4200 |
| 刷新前端子路由 404 | `SpaFallbackFilter` 未生效（必须在 `backend/src/main/java/...` 下） |
| 静态资源 404 或 JS 报 `Unexpected token '<'` | 前端产物未进 jar，或 SPA 回退误把静态请求也转成了 `index.html` |
| 首次构建很慢 | 下载 Node 到 `frontend/.node/`（已被 gitignore），后续复用 |

## 部署

只需分发一个文件：

```bash
java -jar -Xmx512m app.jar --server.port=8080 --spring.profiles.active=prod
```

若修改了 `server.servlet.context-path`（例如 `/app`），必须同步把 `angular.json` 的 `baseHref` 改为 `/app/`。

## 忽略项

`.gitignore` 已排除：`target/`、`node_modules/`、`frontend/dist/`、`frontend/.node/`、`.idea`。
