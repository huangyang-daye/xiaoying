# 素材制作后端

已实现 Java API 的基本业务闭环：分类与模板、演示会话、图片上传与读取、异步制作、三步骤状态、指定阶段失败、个人历史和图片结果。前端已接入这些接口，完整浏览器操作与测试见前端 README。

## 版本与构建

Java 17、Maven 3.9.9、Spring Boot 3.5.16、MyBatis Spring Boot Starter 3.0.5、MySQL 8.4。Flyway、MySQL JDBC、JUnit 和 Testcontainers 的版本由固定的 Spring Boot parent 管理。MyBatis 兼容范围见 [官方说明](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)，Java 要求见 [Spring Boot 3.5 文档](https://docs.spring.io/spring-boot/3.5/system-requirements.html)。

在本目录执行：

```sh
mvn -B -ntp verify
java -jar target/material-studio-0.1.0.jar
```

构建依赖首次从 Maven 仓库下载。没有 Docker 时 H2 契约测试仍执行，MySQL 容器测试会跳过；完整验收使用 `mvn -B -ntp verify -DrequireDocker=true`，该模式在没有 Docker 时失败，避免 CI 静默漏测。运行应用必须连接 MySQL；H2 仅存在于测试依赖。

## 容器启动（推荐）

在仓库根目录使用 PowerShell：

```powershell
Copy-Item .env.example .env
# 编辑 .env 中的本地数据库密码；已有 .env 时保留原配置，不重复覆盖。
docker compose up -d --build
Invoke-RestMethod http://localhost:8080/actuator/health
```

Compose 启动 MySQL、Java 后端与 Next.js，网站入口为 http://localhost:3000。后端端口默认 8080，MySQL 对宿主机默认 13306，以避免占用现有 3306；容器之间仍使用 mysql:3306。若端口被占用，在 `.env` 中修改 MYSQL_PORT/BACKEND_PORT。文件与数据库分别保存在 `backend_files`、`mysql_data` 数据卷。

前端单独开发时也可以只启动 mysql/backend 两个服务，再执行 npm run dev。

若仅用容器运行数据库：先 `docker compose up -d mysql`，再为本地 Java 进程设置：

```powershell
$env:MYSQL_URL = 'jdbc:mysql://localhost:13306/material_studio?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:MYSQL_USER = 'material_app'
$env:MYSQL_PASSWORD = '<与根目录 .env 一致的密码>'
Set-Location backend
mvn spring-boot:run
```

根目录 `.env` 由 Compose 读取；直接运行 Java 不会自动读取它。默认独立 Java JDBC 地址为 localhost:3306，使用 Compose 数据库时按上面设置 13306。

## 初始化与素材

`src/main/resources/db/migration/` 包含 V1 表结构和 V2 初始化数据。Flyway 在 Java 启动时执行一次，并保存版本，不覆盖已有任务；后续修改结构应新增迁移，不能修改已执行脚本。

初始化包含 3 个分类、6 个模板，素材数量分别覆盖 1、2、3。启动时 `FileStorage` 用 Java2D 创建六张 PNG 示例图，文件写入数据卷的 samples 目录，内容是本项目自行绘制的图形与文字，不依赖外部图片网站或付费服务。示例图既可作为测试上传素材，也可作为模拟结果，输出不代表真实合成用户上传的图片。

主要表：template_category、template、asset、production_task、task_asset、task_step。历史直接查询 production_task，结果地址和元信息保存在同一任务行。无需另建一套历史或结果业务表。

## 会话和个人记录

调用 POST `/api/session/demo`，JSON 为 `{"demoKey":"demo-a"}` 或 `{"demoKey":"demo-b"}`。Java 使用随机的服务端 HTTP Session ID，设置 HttpOnly、SameSite=Lax 的 material_session Cookie；身份切换会失效旧会话并新建会话。请求不得通过 ownerId 指定记录归属。

GET `/api/session` 返回当前身份；POST `/api/session/logout` 注销。所有素材、任务、历史和个人结果都从会话确定身份。无会话为 401；他人资源和缺失资源均为 404。分类、模板和示例封面公开读取。

这是一种明确的演示身份方案：任何人都可以选择 A 或 B，同一演示身份共享记录，不属于生产账号认证。服务重启后会话失效，重新选择相同身份可以读取其 MySQL 历史。关闭 DEMO_ENABLED 后演示登录不可用，本期没有额外密码登录入口。

前端可以通过已配置的同源 `/api` 和 `/files` 代理访问。直接跨端口调用需 `credentials: 'include'`，并把浏览器 origin 加入 ALLOWED_ORIGINS；不要混用 localhost 和 127.0.0.1 的 Cookie。对带 Origin 的写请求校验允许列表。

## 接口

| 方法与路径 | 请求参数 | 响应 |
| --- | --- | --- |
| POST /api/session/demo | demoKey：demo-a / demo-b | 200，user{id,displayName}，设置 Cookie |
| GET /api/session | Cookie | 200，user |
| POST /api/session/logout | Cookie | 204 |
| GET /api/categories | 无 | 分类数组，id/name/sortOrder |
| GET /api/templates | categoryId 可选，page=1，pageSize=20 | items/total/page/pageSize |
| GET /api/templates/{id} | 模板 ID | 标题、描述、分类、coverUrl、requiredImageCount、acceptedMimeTypes、maxFileSizeBytes |
| POST /api/assets | multipart：templateId、file | 201，assetId/previewUrl/mimeType/sizeBytes/createdAt |
| POST /api/tasks | templateId、assetIds 有序数组、simulation 可选 | 202，taskId/status/currentStage/queryUrl，Location 指向查询路径 |
| GET /api/tasks/{id} | 任务 ID | 完整任务状态、步骤、错误、结果和时间 |
| GET /api/tasks | status 可重复，page/pageSize | 当前身份历史，按 createdAt DESC/id DESC |
| GET /api/tasks/active | page/pageSize | 当前身份 QUEUED/RUNNING 任务 |
| GET /files/assets/{id} | Cookie | 本人的上传图片 |
| GET /files/results/{id} | Cookie，id 为任务 ID | 本人成功任务的 PNG 结果 |
| GET /files/samples/{key} | 模板 coverUrl 指定 key | 公开示例 PNG |
| GET /actuator/health | 无 | 服务及数据库健康状态 |

page ≥ 1，pageSize 1～50。任务响应字段：id、templateId、templateTitle、status、currentStage、failureStage、errorCode、errorMessage、result、version、createdAt、updatedAt、startedAt、finishedAt、steps。result 仅成功时非空，含 id/url/mimeType/sizeBytes；steps 固定三个，含 stage/sequenceNo/status/开始与结束时间/错误。时间通过 UTC ISO 8601 返回。

上传只接受内容可解码的 JPEG/PNG，先读取尺寸再解码，最大 2000 万像素；单文件全局 10 MiB，种子模板限制 5 MiB。实际图片类型从文件内容识别，不信任扩展名。提交时再次校验素材数量、不可重复、本人归属、存在性及所选模板约束。保存文件名使用 UUID，不拼接用户文件名作为磁盘路径。

错误 JSON 为 errorCode/message/traceId；参数错误 400、无会话 401、功能关闭/来源拒绝 403、缺失或无权访问 404、上传过大 413。查询失败属于请求错误；查询到执行失败任务的 HTTP 状态仍是 200，业务 status 为 FAILED。

## 异步流程及模拟参数

受理事务写入任务、素材顺序与三条 PENDING 步骤后立即返回。Java 定时工作线程按创建时间扫描数据库队列，条件更新 QUEUED→RUNNING 认领；单线程顺序制作，其余任务排队。没有 HTTP 请求驱动步骤执行，也不依赖浏览器持续打开。

整体状态 `QUEUED → RUNNING → SUCCEEDED | FAILED`；阶段 `NOT_STARTED → MATERIAL_CHECK → MOCK_GENERATION → RESULT_ASSEMBLY → COMPLETED`。步骤 `PENDING → RUNNING → SUCCEEDED | FAILED`，失败后的步骤为 SKIPPED。步骤成功和下一阶段开始在同一短事务中提交，等待过程不持有数据库事务；结果写入完成后，最后一步与整体成功一起提交。

```json
{
  "templateId": "demo-pet",
  "assetIds": ["上传接口返回的 assetId"],
  "simulation": {
    "failAtStage": "NONE",
    "materialCheckDelayMs": 1000,
    "mockGenerationDelayMs": 4000,
    "resultAssemblyDelayMs": 1000
  }
}
```

failAtStage 可选 NONE、MATERIAL_CHECK、MOCK_GENERATION、RESULT_ASSEMBLY。每步耗时为 0～30000 的整数，可单独省略以使用默认值。指定阶段等待结束后确定性失败，保存 SIMULATED_STAGE_FAILURE 和失败阶段，后续步骤不执行。请求中的配置在创建时保存，不受之后配置更改影响。未知字段、非法枚举和小数延迟均拒绝。

通过 PowerShell 7 演示（脚本会登录 A、下载本地示例、上传、提交、轮询并检查结果）：

```powershell
pwsh -File backend/scripts/demo.ps1
pwsh -File backend/scripts/demo.ps1 -FailAtStage MATERIAL_CHECK
pwsh -File backend/scripts/demo.ps1 -FailAtStage MOCK_GENERATION
pwsh -File backend/scripts/demo.ps1 -FailAtStage RESULT_ASSEMBLY
pwsh -File backend/scripts/demo.ps1 -DelayMs 5000
```

在多个终端同时执行可观察排队；提交后关闭终端不影响后台制作。重新登录 A 并 GET /api/tasks 可恢复历史；使用 B 查询 A 的任务/素材/结果应为 404。结果预览要求同一会话 Cookie，不能把个人结果 URL 当成公开链接。

## 配置

| 环境变量 | 默认/用途 |
| --- | --- |
| SERVER_PORT | 8080 |
| MYSQL_URL / MYSQL_USER / MYSQL_PASSWORD | JDBC 连接/应用用户/密码；密码无有效默认 |
| STORAGE_ROOT | ./storage；Compose 为 /app/storage |
| DEMO_ENABLED | true；是否允许选择演示身份 |
| SESSION_COOKIE_SECURE | false；通过 HTTPS 部署时设置 true |
| ALLOWED_ORIGINS | localhost/127.0.0.1 的 3000、8080 origin，逗号分隔 |
| SIMULATION_CONTROL_ENABLED | true；false 时拒绝客户端 simulation，并使用后端默认耗时 |
| TASK_DEFAULT_CHECK_DELAY_MS | 1000，整数 0～30000 |
| TASK_DEFAULT_GENERATION_DELAY_MS | 4000，整数 0～30000 |
| TASK_DEFAULT_ASSEMBLY_DELAY_MS | 1000，整数 0～30000 |
| TASK_WORKER_ENABLED | true；测试关闭自动调度以检查每个边界 |
| TASK_DISPATCH_INTERVAL_MS | 500，工作线程空闲时的扫描间隔，须为正数 |

与规划草案的具体差异：实现采用服务端会话而非自制签名 Cookie，无需 SESSION_SIGNING_SECRET；用户 ID 使用固定演示身份键；结果元信息内嵌任务表；模板标题和样本引用在任务创建时固定，素材数量可从关联记录恢复。实现契约以本文和代码为准。

## 测试、边界和后续工作

2026-11-01 验证结果：`mvn -B -ntp verify -DrequireDocker=true` 共 18 项通过，0 失败、0 跳过；包含真实 MySQL 8.4 容器以及启用真实调度器的回归测试。另在独立 Compose 项目中完成镜像构建、启动与健康检查，通过 HTTP 演示成功及三个阶段失败，确认成功图片可读取；重启后重新登录，同一历史终态与结果文件仍可读取。验收结束后已停止临时容器，数据卷保留。GitHub Actions 尚未推送触发；该条为后端阶段的历史验证记录；后续前端联调结果见前端 README。

H2 与 Testcontainers MySQL 执行相同契约：分类、校验、上传、任务成功、三个阶段失败、中断后跳过、用户隔离、只读查询、终态不回退、刷新后新会话读取、多任务及并发认领。GitHub Actions 在 push/pull_request 执行 Java verify，要求 Docker；前端检查继续由独立 workflow 执行。

本期为单后端实例。重启时把遗留 RUNNING 标记 FAILED/RESTART_INTERRUPTED，QUEUED 继续调度，已结束任务保留；不实现自动续跑、多实例协调、自动重试或提交幂等。若提交响应丢失，先查历史再决定重提。数据库临时不可用导致状态写入失败时可能留下 RUNNING，需要恢复数据库后重启归档，不承诺自动恢复执行。

素材和结果不提供删除，未被任务引用的素材和异常中断产生的孤立文件暂不自动回收。未提供用户配额或生产认证。Flyway 在 MySQL 8.4 上可能提示其声明测试版本较旧，须以实际 MySQL 契约测试结果核验。

部署升级前备份数据库与文件卷，构建新镜像后 `docker compose up -d --build backend`。回滚使用先前验证过的源码/镜像版本，并确认数据库迁移向后兼容；不兼容时从备份恢复，不能通过删卷回滚。停止服务使用 `docker compose down`，默认保留卷。

本步使用 AI 辅助开发：从 R-01～R-14 拆分接口、持久化和状态机，先生成最小工程与迁移，再实现工作线程和失败注入，使用同一契约在 H2/MySQL 检查，最后以容器 HTTP 演示验证自动调度。具体修正包含拒绝小数延迟，以及把步骤结束和下一步开始合并为一个事务。依赖复用 Spring、MyBatis 等开源库，图形为本项目代码生成，没有复用外部业务代码。实际工作跨度以聊天工具记录为准，未做人工工时计量；尚未创建本阶段验收 commit。
