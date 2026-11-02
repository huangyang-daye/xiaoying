# 映作 · 素材制作网站

一个可独立运行的素材制作网站：浏览模板、上传图片、提交后台制作任务，再查看个人历史和图片结果。制作使用可控制耗时与失败阶段的模拟生成器，无需真实 AI 服务。

## 当前实现

已完成前后端业务闭环，业务接口和持久化由 Java 提供，Next.js 通过同源代理访问 Java。

- 模板：3 个分类、6 个模板，覆盖 1、2、3 张图片要求，支持分类筛选和详情。
- 上传：JPEG/PNG 校验、服务端保存、预览、替换、移除和错误反馈；前后端校验数量、类型和大小。
- 制作：受理后返回任务 ID，由 Java 后台顺序执行“素材检查 → 模拟生成 → 结果整理”，支持连续提交、多任务并行及指定阶段失败。
- 状态：独立跟踪各任务，查询异常保留原状态并支持恢复；终态停止轮询，刷新后从 Java 恢复历史和活动任务。
- 身份与结果：可用邮箱、用户名和密码注册；也能一键进入演示用户 A/B。素材、任务和结果按账户隔离，成功结果可预览。
- 工程交付：数据库迁移和初始化数据、容器配置、关键测试及 GitHub Actions 已提供。

## 技术版本

| 层次 | 版本与职责 |
| --- | --- |
| 前端 | Node.js 24.22.1、Next.js 16.4.3 App Router、React 19.3.1、TypeScript 5.9.3；页面与异步状态 |
| 后端 | Java 17、Maven 3.9.9、Spring Boot 3.5.16、MyBatis Starter 3.0.5；接口、校验、后台制作与持久化 |
| 数据与文件 | MySQL 8.4、Flyway；数据库保存任务及步骤，文件卷保存素材、示例及历史文件结果；新生成结果存入 MySQL |
| 测试 | Vitest 5.0.5、Playwright 1.64.1、JUnit、H2 契约测试及 Testcontainers MySQL 测试 |

前端依赖固定在 `frontend/package.json` 和 `package-lock.json`；Java 依赖由 `backend/pom.xml` 的固定版本及 Spring Boot parent 管理。Node.js 版本见 `frontend/.nvmrc`。

## 快速启动

准备 Docker Desktop 或 Docker Engine，以及 Docker Compose v2。在仓库根目录执行：

```powershell
# 仅首次复制；已有 .env 时保留原配置。
Copy-Item .env.example .env
# 编辑 .env，为 MYSQL_ROOT_PASSWORD 和 MYSQL_PASSWORD 设置本地密码。
docker compose up -d --build --wait
```

首次构建需要联网下载基础镜像和依赖。启动后访问：

| 入口 | 默认地址 |
| --- | --- |
| 网站 | http://localhost:3000 |
| Java 健康检查 | http://localhost:8080/actuator/health |
| MySQL 宿主机连接 | localhost:13306，数据库 material_studio，用户 material_app |

容器间使用 `backend:8080` 和 `mysql:3306`。浏览器的 `/api/*`、`/files/*` 请求经 Next.js 转发至 Java，保留会话 Cookie。端口默认只绑定宿主机回环地址。

`MYSQL_PORT`、`BACKEND_PORT`、`FRONTEND_PORT` 和 `ALLOWED_ORIGINS` 在根目录 `.env` 中配置；更改网站端口或域名时，将实际浏览器 origin 加入 `ALLOWED_ORIGINS`。`.env.example` 只含演示值，真实 `.env` 不纳入 Git。Java 的其他环境变量见 [后端配置说明](backend/README.md#配置)，单独运行前端时的 `JAVA_INTERNAL_URL` 见 [前端启动说明](frontend/README.md#启动)。

Flyway 在 Java 启动时执行 `backend/src/main/resources/db/migration/` 中的表结构与种子迁移。Java 自动生成六张本地 PNG 示例图，无需额外下载素材。文件保存在 `backend_files` 卷，数据库保存在 `mysql_data` 卷。

```sh
# 查看服务及最近日志
docker compose ps
docker compose logs --tail 100
# 停止服务，保留数据库和文件卷
docker compose down
```

## 演示流程

演示账户：

| 账户 | 用户名 | 邮箱 | 密码 |
| --- | --- | --- | --- |
| 演示用户 A | demo-a | demo-a@studio.local | demo-password |
| 演示用户 B | demo-b | demo-b@studio.local | demo-password |

顶部可以直接选择这两个身份，也可以在登录页用上表的用户名或邮箱加密码进入。同一演示账户的记录是共享的。

1. 打开网站。可在顶部选择演示用户 A，或打开「注册」依次填写邮箱、用户名和密码。浏览分类，选择模板并上传规定数量的 JPEG/PNG，每张不超过 5 MiB。可先保存模板封面作为测试图片。
2. 提交制作，收到任务 ID 后可继续浏览或再次提交。默认三阶段耗时分别为 1000、4000、1000 ms；默认 4 个 Java 工作线程并行执行，超出并发上限的任务在数据库排队；通过 `TASK_WORKER_CONCURRENCY` 配置 1～32 个并发任务。
3. 在详情页展开“演示控制”，启用自定义参数，可指定素材检查、模拟生成或结果整理失败，并为每阶段设置 0～30000 ms 的整数耗时。失败后后续步骤跳过。
4. 前往“我的作品”查看状态、阶段和失败原因；成功后点击图片预览结果。结果是预置示例图，不进行真实图片合成。
5. 连续提交两个任务，或在处理中刷新页面，观察独立跟踪和恢复。关闭页面不影响 Java 执行；查询网络异常不会把任务改为失败，可点击“恢复查询”。
6. 切换用户 B，查看独立记录；切回 A 可恢复 A 的历史。自行注册的账户只对本人可见。个人设置可修改昵称、头像和密码；页面最下方可删除账户，并同时删除该账户的素材、任务、结果和自建模板。

API 演示脚本及指定阶段失败、耗时控制命令见 [后端模拟演示](backend/README.md#异步流程及模拟参数)。演示脚本要求 PowerShell 7；正式验收的项目构建、测试和执行应置于容器隔离环境中。

## 状态、接口与身份

任务整体状态为 `QUEUED → RUNNING → SUCCEEDED | FAILED`。三个执行阶段为 `MATERIAL_CHECK → MOCK_GENERATION → RESULT_ASSEMBLY`，每阶段独立落库，失败时记录失败阶段与原因，后续步骤置为 `SKIPPED`。Java 通过数据库队列认领任务并执行，查询请求只读取状态。

Java 使用服务端 HTTP Session 和 HttpOnly、SameSite=Lax 的 `material_session` Cookie 确定身份。密码以 PBKDF2-HMAC-SHA256 保存，不保存明文。客户端不能用 `ownerId` 指定资源归属；无会话返回 401，他人素材、任务或结果返回 404。公共模板及示例图片公开访问，自建模板只对创建者可见。

| 接口 | 主要输入与输出 |
| --- | --- |
| POST /api/accounts | 邮箱、用户名、密码、可选昵称；201 返回 user 并设置 Cookie |
| POST /api/session/login | 用户名或邮箱、密码；返回 user 并设置 Cookie |
| POST /api/session/demo | 输入 demoKey：demo-a / demo-b；返回 user 并设置 Cookie |
| PATCH /api/account、POST /api/account/password、POST /api/account/avatar | 修改昵称、重置密码、上传头像 |
| POST /api/account/delete | 当前密码；删除账户及其素材、任务、结果和自建模板 |
| GET /api/categories、GET /api/templates | 分类列表；模板支持 categoryId、page、pageSize，返回 items/total/page/pageSize |
| GET /api/templates/{id} | 返回标题、描述、coverUrl、requiredImageCount、acceptedMimeTypes、maxFileSizeBytes |
| POST /api/assets | multipart：templateId、file；返回 assetId、previewUrl、mimeType、sizeBytes |
| POST /api/tasks | 输入 templateId、assetIds、可选 simulation；202 返回 taskId、status、currentStage、queryUrl |
| GET /api/tasks/{id} | 返回任务状态、阶段、steps、failureStage、errorMessage、result 及时间 |
| GET /api/tasks、GET /api/tasks/active | 当前身份的分页历史、未结束任务 |
| GET /files/assets/{id}、GET /files/results/{id} | 当前身份的素材、成功结果图片 |

完整字段、校验和错误响应见 [Java API 契约](backend/README.md#接口)。

## 构建与测试

以下为工程内部命令；运行验收时在容器或隔离的 CI 环境执行。日常启动优先使用上述 Compose，独立开发连接配置见各工程 README。

```sh
# 在前端开发或 CI 容器中，从仓库根目录进入工程
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
# 开发服务：npm run dev；生产构建后服务：npm start
```

```sh
# 在可访问 Docker 的 Java 测试容器或隔离 CI 环境中
cd backend
mvn -B -ntp verify -DrequireDocker=true
# 已配置 MySQL 时，可启动生成的 jar
java -jar target/material-studio-0.1.0.jar
```

浏览器联调先启动整站，在前端测试容器中安装 Chromium（`npx playwright install --with-deps chromium`），再执行 `npm run test:e2e`；按需设置 `PLAYWRIGHT_BASE_URL` 并同步允许来源。完整流程见 [前端测试说明](frontend/README.md#检查)。

`.github/workflows/` 在 push / pull request 时分别执行前端 lint、类型、状态测试与构建，Java 测试与构建，以及整站 Compose 和浏览器联调。

2026-11-01 对实现版本 `6b7465d` 对应源码完成的本地容器复核：

| 检查 | 结果 |
| --- | --- |
| 前端 lint、类型检查、生产构建 | 通过 |
| TaskTracker 状态测试 | 4 项通过 |
| Java H2 契约与真实调度器测试 | 9 项通过，0 失败、0 跳过 |
| Compose 整站启动及真实 MySQL HTTP 流程 | 成功、三个阶段失败、后续跳过、结果读取和身份隔离通过 |
| Chromium 真实浏览器流程 | 2 项通过，含刷新恢复、多任务、查询异常恢复和 390 px 布局检查 |

此前 Java 完整验证报告为 18 项通过，包含 Testcontainers MySQL；本轮复核未重复运行该 MySQL 测试类，使用真实 MySQL 整站及 HTTP 流程另行核实。本地通过不代表 GitHub 托管 CI 已通过，目前没有已确认的云端运行结果。此前联调范围见 [验收记录](docs/integration-validation.md)。

## 多任务并发与事务一致性

2026-11-02 已通过完整 Java 构建和 39 项测试（含真实 MySQL，0 跳过），以及前端 lint、类型、构建和 8 项测试。改造为有界工作池：调度线程只派发执行机会，工作线程取得空闲名额后才从数据库认领任务；内存不保存待执行业务队列。每个任务内部的三个阶段仍顺序执行，不同任务可以并行。详见 [事务与一致性设计](docs/async-consistency.md)。

受理、认领、每次阶段迁移和失败归档分别使用短事务。认领使用 `FOR UPDATE SKIP LOCKED`；状态迁移先锁定任务行，再修改步骤，并通过 `version` 条件更新防止覆盖。耗时模拟和图片准备在事务外执行。新本地生成结果的二进制内容写入 V6 新增的 `task_result` 表，与最终步骤和 `SUCCEEDED` 状态原子提交；旧文件结果继续兼容读取。

刷新恢复活动任务使用 `nextCursor` 和 `before` 游标，避免并行任务结束导致分页偏移而遗漏任务。账户删除与提交通过账户行锁串行化，删除与状态迁移通过任务行锁协调，文件清理仅在删除事务提交后执行。

ACID 保证作用于 InnoDB 中的业务记录及新本地生成结果。上传文件、旧文件结果和外部图片 URL 不属于数据库事务；不承诺跨 MySQL、文件系统和外部资源的分布式原子提交。

## 限制与未完成项

- 当前为单后端实例。注册账户使用邮箱、用户名和密码；演示用户 A/B 仍是共享的公开身份。没有配额、真实 AI、第三方登录或素材管理后台。
- 服务重启后会话失效，重新登录或选择相同演示身份可读持久化历史。遗留 `RUNNING` 任务归档为 `FAILED / RESTART_INTERRUPTED`，`QUEUED` 任务继续调度；不自动续跑或重试。删除账户会删除该账户的个人记录；未提供单独的素材删除及孤立文件自动清理。
- 未实现提交幂等；如果提交响应丢失，应先查历史再决定重提。数据库故障可能留下 `RUNNING`，恢复数据库后需重启归档。
- 前端开发依赖的兼容性和审计边界见 [前端说明](frontend/README.md#windows-文件占用)。

## 部署与回滚

升级前备份 MySQL 数据及 `backend_files` 文件卷，并记录已验证的源码 commit 或镜像版本。更新源码和配置后，在根目录执行 `docker compose up -d --build --wait`，随后检查网站、Java 健康接口以及已有历史和结果。

回滚时切换到先前验证过的源码或镜像版本并重新启动服务，确认数据库迁移向后兼容；不兼容时从配套备份恢复数据库与文件。停止服务使用 `docker compose down`，不要通过删除数据卷回滚。

## AI 工作流、来源与投入

本项目使用 AI 编程辅助：按 R-01～R-14 拆解接口、状态机和页面，提供任务要求与 Java 契约，生成并逐步修改工程，再通过静态检查、状态测试、后端契约测试和真实容器联调修正问题。例如拒绝小数模拟耗时、将步骤完成与下一阶段开始合并为同一事务，并验证查询异常不会改变任务业务状态。

主要复用 Next.js 工程脚手架及 Spring Boot、MyBatis、Flyway 等开源依赖；页面和业务代码在本仓库实现。模板封面和模拟结果由本项目 Java2D 代码生成；根目录 PNG 为随题参考截图，未作为生产模板素材使用。

可核对的项目工作日期范围为 2026-10-31～2026-11-01，包含初始化与规划、Java 业务与测试、前端对接和容器验收。该范围是日历跨度，不等于有效工时；开发者人工投入和 AI 执行时长未独立计时，暂无法提供准确小时数。

## 项目结构与验收版本

```text
frontend/       Next.js 页面、状态管理、Vitest 与 Playwright 测试
backend/        Java API、MyBatis、Flyway 迁移、后台任务与测试
.github/        前端、后端及整站联调 CI
compose.yaml    Next.js、Java、MySQL 及持久化数据卷
.env.example    非敏感的本地配置示例
docs/           规划与联调说明
任务要求.md     R-01～R-14 原始要求
```

实现验收提交为 `6b7465ddef45dd430257939032a7f3e0f6982170`，主分支 `main`。该提交已在本地保存，尚未确认推送到远程；远程地址通过 `git remote -v` 查看。该提交是此前的实现基线；2026-11-02 的并发与事务改造属于当前工作区后续变更。

[实现方案](docs/implementation-plan.md)、[参数字典](docs/parameter-catalog.md) 和 [初始架构规划](docs/architecture.md) 保留规划阶段的候选设计；实际接口、配置和边界以当前源码、[前端说明](frontend/README.md) 及 [后端说明](backend/README.md) 为准。
