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

`src/main/resources/db/migration/` 包含 V1 表结构、V2 初始化数据、V3/V4 模板链接及归属、V5 账户、V6 事务结果存储及 V7 幂等、租约和重试迁移。Flyway 在 Java 启动时执行一次，并保存版本，不覆盖已有任务；后续修改结构应新增迁移，不能修改已执行脚本。

初始化包含 3 个分类、6 个模板，素材数量分别覆盖 1、2、3。启动时 `FileStorage` 用 Java2D 创建六张 PNG 示例图，文件写入数据卷的 samples 目录，内容是本项目自行绘制的图形与文字，不依赖外部图片网站或付费服务。示例图既可作为测试上传素材，也可作为模拟结果，输出不代表真实合成用户上传的图片。

主要表：app_user、template_category、template、asset、production_task、task_asset、task_step、task_result、task_attempt。历史直接查询 production_task，结果元信息在任务行中；V6 后新本地生成结果的字节保存在 task_result，与成功状态一并提交。旧结果仍按 result_key 读取文件。

## 会话和个人记录

账户保存在 `app_user`。注册提交邮箱、用户名和密码，用户名与邮箱会转成小写并要求唯一；密码使用 PBKDF2-HMAC-SHA256，120000 次迭代，不保存明文。登录接受用户名或邮箱。会话只保存用户 ID，每次读取当前资料，因此昵称和头像修改会立即反映到后续请求。

调用 POST `/api/session/demo`，JSON 为 `{"demoKey":"demo-a"}` 或 `{"demoKey":"demo-b"}`。Java 使用随机的服务端 HTTP Session ID，设置 HttpOnly、SameSite=Lax 的 material_session Cookie；身份切换会失效旧会话并新建会话。请求不得通过 ownerId 指定记录归属。

GET `/api/session` 返回当前身份；POST `/api/session/logout` 注销。所有素材、任务、历史和个人结果都从会话确定身份。无会话为 401；他人资源和缺失资源均为 404。公共分类、模板和示例封面公开读取。自建模板只对创建者可见。

演示用户 A/B 会在启动时写入数据库，用户名分别是 demo-a、demo-b，初始密码都是 `demo-password`，同一演示身份共享记录。关闭 DEMO_ENABLED 后不能再一键选择演示身份，仍可用用户名和密码登录。个人设置可修改昵称、上传 JPEG/PNG 头像（最大 2 MB）和重置密码。删除账户需要当前密码，并删除该账户的素材、任务、结果文件和自建模板；若演示账户被删除，下次选择该演示身份会重新创建一个空账户。服务重启后会话失效，重新登录同一账户可以读取其 MySQL 历史。

前端可以通过已配置的同源 `/api` 和 `/files` 代理访问。直接跨端口调用需 `credentials: 'include'`，并把浏览器 origin 加入 ALLOWED_ORIGINS；不要混用 localhost 和 127.0.0.1 的 Cookie。对带 Origin 的写请求校验允许列表。

## 接口

| 方法与路径 | 请求参数 | 响应 |
| --- | --- | --- |
| POST /api/accounts | email、username、password、displayName 可选 | 201，user，设置 Cookie |
| POST /api/session/login | login（用户名或邮箱）、password | 200，user，设置 Cookie |
| POST /api/session/demo | demoKey：demo-a / demo-b | 200，user，设置 Cookie |
| GET /api/session | Cookie | 200，user{id,username,email,displayName,avatarUrl} |
| POST /api/session/logout | Cookie | 204 |
| PATCH /api/account | displayName | 200，user |
| POST /api/account/password | currentPassword、newPassword | 200，user |
| POST /api/account/avatar | multipart：file | 200，user，avatarUrl |
| POST /api/account/delete | password | 204，删除账户及个人记录 |
| GET /api/categories | 无 | 分类数组，id/name/sortOrder |
| GET /api/templates | categoryId 可选，page=1，pageSize=20 | items/total/page/pageSize |
| GET /api/templates/{id} | 模板 ID | 标题、描述、分类、coverUrl、requiredImageCount、acceptedMimeTypes、maxFileSizeBytes |
| POST /api/assets | multipart：templateId、file | 201，assetId/previewUrl/mimeType/sizeBytes/createdAt |
| POST /api/tasks | templateId、assetIds 有序数组、simulation 可选；Idempotency-Key 可选请求头（网站始终发送） | 202，taskId/status/currentStage/queryUrl，Location 指向查询路径 |
| GET /api/tasks/{id} | 任务 ID | 完整任务状态、步骤、错误、结果和时间 |
| GET /api/tasks | status 可重复，page/pageSize | 当前身份历史，按 createdAt DESC/id DESC |
| GET /api/tasks/active | page/pageSize | 当前身份 QUEUED/RUNNING 任务 |
| GET /files/assets/{id} | Cookie | 本人的上传图片 |
| GET /files/results/{id} | Cookie，id 为任务 ID | 本人成功任务的 PNG 结果 |
| GET /files/avatars/{id} | Cookie，且 id 为当前用户 | 本人头像；他人或未设置均为 404 |
| GET /files/samples/{key} | 模板 coverUrl 指定 key | 公开示例 PNG |
| GET /actuator/health | 无 | 服务及数据库健康状态 |

page ≥ 1，pageSize 1～50。任务响应字段：id、templateId、templateTitle、status、currentStage、failureStage、errorCode、errorMessage、result、version、createdAt、updatedAt、startedAt、finishedAt、steps。result 仅成功时非空，含 id/url/mimeType/sizeBytes；steps 固定三个，含 stage/sequenceNo/status/开始与结束时间/错误。时间通过 UTC ISO 8601 返回。

上传只接受内容可解码的 JPEG/PNG，先读取尺寸再解码，最大 2000 万像素；单文件全局 10 MiB，种子模板限制 5 MiB。实际图片类型从文件内容识别，不信任扩展名。提交时再次校验素材数量、不可重复、本人归属、存在性及所选模板约束。保存文件名使用 UUID，不拼接用户文件名作为磁盘路径。

错误 JSON 为 errorCode/message/traceId；参数错误 400、无会话 401、功能关闭/来源拒绝 403、缺失或无权访问 404、上传过大 413。查询失败属于请求错误；查询到执行失败任务的 HTTP 状态仍是 200，业务 status 为 FAILED。

## 异步流程及模拟参数

受理事务写入任务、素材顺序与三条 PENDING 步骤后立即返回。Java 定时工作线程按创建时间扫描数据库队列，`FOR UPDATE SKIP LOCKED` 锁定后条件更新 QUEUED→RUNNING 认领；默认 4 个工作线程并行制作，超出并发上限的任务保留在数据库队列中；各任务内部仍按阶段顺序执行。没有 HTTP 请求驱动步骤执行，也不依赖浏览器持续打开。

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
| TASK_MAX_ATTEMPTS | 3；总执行次数 1～6，受理时快照 |
| TASK_RETRY_BASE_DELAY_MS / TASK_RETRY_MAX_DELAY_MS | 1000 / 30000；正数且基础不大于上限，上限至多 300000；受理时快照 |
| TASK_LEASE_MS / TASK_HEARTBEAT_INTERVAL_MS | 120000 / 1000；租约 3000～3600000，心跳至少 100 且小于租约一半 |
| TASK_WORKER_CONCURRENCY | 4；每实例并发任务数，1～32；Compose 可从 .env 配置 |
| TASK_WORKER_ENABLED | true；测试关闭自动调度以检查每个边界 |
| TASK_DISPATCH_INTERVAL_MS | 500，工作线程空闲时的扫描间隔，须为正数 |

与规划草案的具体差异：实现采用服务端会话而非自制签名 Cookie，无需 SESSION_SIGNING_SECRET；演示用户沿用固定 ID，注册用户使用 32 位随机 ID；结果元信息内嵌任务表；模板标题和样本引用在任务创建时固定，素材数量可从关联记录恢复。实现契约以本文和代码为准。

## 幂等、检查点与重试

本次提交新增 V7：账户作用域幂等键、请求指纹、执行租约、尝试预算、下次执行时间及 task_attempt 历史。同键同参数 202 返回原任务并设置 Idempotency-Replayed: true，不同参数返回 409 / IDEMPOTENCY_CONFLICT；省略键的旧调用仍按非幂等受理。键保留至任务被删除。

默认最多 3 次执行，包含首次、自动重试和中断恢复，采用 1 秒基础、30 秒封顶的指数退避。暂时性数据库/IO 错误及模拟暂时故障可重试；素材缺失、明确的 failAtStage 和普通程序错误不自动重试。成功阶段保持检查点，失败阶段重做；只恢复过期租约，旧令牌不能提交。查询返回 attemptCount、maxAttempts、nextAttemptAt、lastErrorCode/Message、attempts 及每步骤 attemptCount。

完整方案比较、指纹规则、恢复语义、演示和迁移见 [任务可靠性设计](../docs/task-reliability.md)。脚本可用 -ReplayAcceptance 验证同一任务重放；-TransientFailAtStage MOCK_GENERATION -TransientFailureCount 1 -DelayMs 100 演示自动重试成功。

## 事务与结果一致性

认领、阶段迁移和失败记录均为独立短事务（写事务 READ_COMMITTED），先锁定任务父行，再修改步骤；任务更新额外校验 version。受理事务创建任务、素材顺序和三个步骤，未提交记录不会被工作线程认领。历史与详情使用 REPEATABLE_READ，避免读到任务和步骤的不同提交版本。等待和图片准备不持有事务锁。

V6 新增 task_result，使用 task_id 主键及级联外键，保存新本地模拟结果的二进制内容，与最后步骤、结果大小和成功状态一并提交；不再为新任务写 results 文件。查询结果地址不变，旧结果仍按 result_key 从文件卷读取。外链结果只对数据库元信息提供事务一致性，不承诺外部资源的可用性。

活动任务查询新增 before（上一页 nextCursor），按 created_at/id 稳定游标扫描；保留 page/pageSize 供旧调用使用，前端恢复采用游标以应对并行完成。账户删除先锁账户及所属任务，结果记录级联删除，文件在 afterCommit 回调清理；回滚时不提前删除文件。文件清理失败可能留下孤立文件，但不会因为删除事务回滚而丢失已保留记录的文件。

设计、ACID 边界与测试场景见 [并发与一致性设计](../docs/async-consistency.md)。

## 测试、边界和后续工作

2026-11-02 当前可靠性扩展：完整 verify 和 jar 构建共 58 项通过（27 MySQL、27 H2、3 调度器、1 Docker 检查），0 失败/跳过；前端 13 项测试及 4 项 Chromium 联调通过。另实际 SIGKILL 并重启隔离后端，验证原任务从已提交检查点恢复、旧尝试租约过期、幂等重放返回同一 ID 和最终结果可读。

2026-11-02 并发改造验证：容器内完整 verify 共 39 项通过（含真实 MySQL 8.4），0 失败、0 跳过，jar 构建通过；包括多工作线程唯一认领、有界并行、受理/阶段/删除回滚、成功失败竞争和稳定游标恢复。以下 2026-11-01 记录为改造前历史结果。

2026-11-01 验证结果：`mvn -B -ntp verify -DrequireDocker=true` 共 18 项通过，0 失败、0 跳过；包含真实 MySQL 8.4 容器以及启用真实调度器的回归测试。另在独立 Compose 项目中完成镜像构建、启动与健康检查，通过 HTTP 演示成功及三个阶段失败，确认成功图片可读取；重启后重新登录，同一历史终态与结果文件仍可读取。验收结束后已停止临时容器，数据卷保留。当时 GitHub Actions 尚未推送触发；该条仅为后端阶段的历史验证记录，不代表当前远端状态；后续前端联调结果见前端 README。

H2 与 Testcontainers MySQL 执行相同契约：分类、校验、上传、任务成功、三个阶段失败、中断后跳过、用户隔离、只读查询、终态不回退、刷新后新会话读取、多任务及并发认领。GitHub Actions 在 push/pull_request 执行 Java verify，要求 Docker；前端检查继续由独立 workflow 执行。

本期为单后端实例。重启及周期扫描只恢复过期 RUNNING，从当前阶段续跑并共用尝试预算；有效租约保留。QUEUED 按 next_attempt_at 调度，终态不复活。数据库暂不可用时保留已提交状态，恢复连接后继续扫描。幂等要求复用同一键；最终失败不支持手动原地重试，多实例共享素材/会话及完整集群运营未验收。

未提供单条素材或作品删除；删除账户会删除该账户的记录与对应文件。未被任务引用的素材和异常产生的孤立文件暂不自动回收。已提供注册与密码登录，尚无邮箱验证、忘记密码、配额或完整生产账号运营能力。Flyway 在 MySQL 8.4 上可能提示其声明测试版本较旧，须以实际 MySQL 契约测试结果核验。

部署升级前备份数据库与文件卷，构建新镜像后 `docker compose up -d --build backend`。回滚使用先前验证过的源码/镜像版本，并确认数据库迁移向后兼容；不兼容时从备份恢复，不能通过删卷回滚。停止服务使用 `docker compose down`，默认保留卷。

本步使用 AI 辅助开发：从 R-01～R-14 拆分接口、持久化和状态机，先生成最小工程与迁移，再实现工作线程和失败注入，使用同一契约在 H2/MySQL 检查，最后以容器 HTTP 演示验证自动调度。具体修正包含拒绝小数延迟，以及把步骤结束和下一步开始合并为一个事务。依赖复用 Spring、MyBatis 等开源库，图形为本项目代码生成，没有复用外部业务代码。此前并发事务验收 commit 为 `730d70d3ff55b1c2601a8b51162b3e00c1c69371`；V7 可靠性扩展随本次提交保存；本次文档更新未核验远程同步或托管 CI。工作日期范围和未独立计时的投入说明见[根目录 README](../README.md#ai-工作流来源与投入)。
