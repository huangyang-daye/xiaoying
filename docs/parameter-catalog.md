# 参数与状态字典（规划稿）

## 1. 阅读规则

本文配套 [实现方案](implementation-plan.md)。所有字段名和默认值均为建议契约，尚未实现；“必选”仅指后续按该方案实现时的校验规则。

“每个参数的可选状态值”按类型区分：枚举列出完整候选集合；数字列出范围与建议值；文本、ID、时间、URL 列出格式与来源，它们不是可以穷举的状态。架构替代方案与运行期枚举分开，不能将未选择的方案误当成必须全部实现。

## 2. 架构选择参数

| 参数 | 可选值 | 建议默认 | 说明 |
| --- | --- | --- | --- |
| apiAccessMode | SAME_ORIGIN_PROXY / DIRECT_JAVA | SAME_ORIGIN_PROXY | 前者 Next.js 转发；后者需 CORS 和凭据策略；两者业务都由 Java 执行 |
| identityMode | DEMO_SESSION / LOCAL_ACCOUNT | DEMO_SESSION | 演示 A/B 会话即可；LOCAL_ACCOUNT 需额外设计密码存储和登录，不纳入建议基线 |
| taskExecutionMode | DB_QUEUE_SINGLE_WORKER / DB_QUEUE_BOUNDED_POOL / MESSAGE_QUEUE | DB_QUEUE_SINGLE_WORKER | 数据库排队、Java 顺序处理已满足题意；后两种为替代方案 |
| statusUpdateMode | POLLING / SSE / WEBSOCKET | POLLING | 不要求同时实现三种方式 |
| frontendStateMode | REACT_REDUCER / QUERY_LIBRARY | REACT_REDUCER | 若改用查询库，仍需验证身份隔离和终态停止 |
| fileStorageMode | LOCAL_VOLUME / OBJECT_STORAGE | LOCAL_VOLUME | 基线使用持久化数据卷；对象存储非必需 |
| templateSource | DATABASE_SEED / BACKEND_SEED_FILE | DATABASE_SEED | Java 提供模板，不能仅前端硬编码 |
| simulationControlMode | DEMO_REQUEST / SERVER_CONFIG | DEMO_REQUEST | 演示配置允许请求传值；关闭时用后端默认参数并拒绝传入 simulation |
| restartPolicy | FAIL_INTERRUPTED / RESUME_WITH_CHECKPOINT | FAIL_INTERRUPTED | 不续跑 RUNNING 任务；续跑为范围外扩展 |
| deliveryMode | COMPOSE / MANUAL_CONTAINER_SETUP | COMPOSE | 两者均需源码、配置及初始化说明；Compose 为推荐项 |
| dependencyVersionPolicy | VERIFIED_PINNED_STABLE | VERIFIED_PINNED_STABLE | P1 核验 Next/React/Node、Java/Spring/MyBatis、MySQL/驱动组合后记录准确版本与锁文件，当前不伪造版本结论 |

## 3. 后端持久化状态（完整枚举）

| 参数 | 可选值 | 含义与默认 |
| --- | --- | --- |
| task.status | QUEUED / RUNNING / SUCCEEDED / FAILED | 等待/处理/成功/失败；创建默认 QUEUED |
| task.currentStage | NOT_STARTED / MATERIAL_CHECK / MOCK_GENERATION / RESULT_ASSEMBLY / COMPLETED | 未开始/素材检查/模拟生成/结果整理/完成；默认 NOT_STARTED |
| task.failureStage | null / MATERIAL_CHECK / MOCK_GENERATION / RESULT_ASSEMBLY | 默认 null；阶段失败时必须赋值；调度前失败可为 null |
| step.stage | MATERIAL_CHECK / MOCK_GENERATION / RESULT_ASSEMBLY | 固定三步，sequence 分别为 1/2/3 |
| step.status | PENDING / RUNNING / SUCCEEDED / FAILED / SKIPPED | 默认 PENDING；失败后未执行步骤为 SKIPPED |

组合约束：

| task.status | currentStage 合法值 | 其他约束 |
| --- | --- | --- |
| QUEUED | NOT_STARTED | 三步骤 PENDING；没有结果和失败原因 |
| RUNNING | 三个执行阶段之一 | 当前步骤 RUNNING，前序成功，后序待执行；状态事务只暴露一致快照 |
| SUCCEEDED | COMPLETED | 三步骤 SUCCEEDED；结果非空；failureStage 和错误为空 |
| FAILED | NOT_STARTED 或失败发生的执行阶段 | errorCode/errorMessage 非空；步骤失败时 failureStage 为对应阶段；重启中断按最后执行阶段记录 |

不增加 CANCELLED、RETRYING、PAUSED 等未要求状态。终态为 SUCCEEDED/FAILED，只读查询不得修改终态。

## 4. 前端局部状态（不写成任务执行状态）

| 参数 | 可选值 | 默认与交互 |
| --- | --- | --- |
| sessionState | LOADING / READY / UNAUTHENTICATED / SWITCHING / ERROR | 初始化 LOADING；切换中禁止旧身份继续提交 |
| resourceLoadState | IDLE / LOADING / SUCCESS / EMPTY / NOT_FOUND / ERROR | 用于列表/模板详情/历史；EMPTY 是合法空数据，NOT_FOUND 是详情不存在 |
| uploadSlot.status | EMPTY / UPLOADING / READY / ERROR | 默认 EMPTY；选择合法文件后上传，成功才有可提交 assetId |
| submitState | IDLE / SUBMITTING / ERROR | 默认 IDLE；受理成功恢复 IDLE，并独立展示任务 |
| taskQuery.status | IDLE / FETCHING / OK / ERROR | 默认 IDLE；ERROR 不改变后端 task.status |
| trackingState | STOPPED / SCHEDULED / FETCHING / PAUSED_ERROR | 非终态安排查询；请求错误暂停；终态/卸载/身份切换停止 |
| previewState | CLOSED / OPEN / LOAD_ERROR | 默认 CLOSED；成功结果支持图片预览和加载失败反馈 |

提交按钮可用条件：会话 READY、模板成功加载、槽位数正确且全部 READY、素材满足约束、submitState 不为 SUBMITTING。失败任务不会永久锁住下一次提交。

## 5. 模板与上传参数

| 参数 | 类型/可选值或范围 | 必选/默认/校验 |
| --- | --- | --- |
| template.id | 非空不透明字符串；种子可用 demo-postcard 等 | 服务端生成/种子定义；不能由前端虚构有效模板 |
| template.title | 文本，1～80 字符 | 必选；按纯文本渲染 |
| template.description | 文本，0～1000 字符 | 默认空文本 |
| template.categoryId | 有效分类 ID | 必选；分类名为展示文案 |
| category.id / name | 非空 ID / 1～40 字符文本 | 建议种子 creative、portrait、pet；显示创意图片、人像风格、萌宠 |
| category.sortOrder | 整数 ≥ 0 | 默认 0；ID 作为同序稳定排序辅助键 |
| template.coverUrl / sampleResultUrl | Java 可访问的种子资源 URL | 必选；内部保存 storageKey，由服务端拼接公开读取 URL |
| template.requiredImageCount | 整数 1～3 | 每模板必选；示例分别覆盖 1、2、3，不允许用户任意改写 |
| template.acceptedMimeTypes | 非空子集：image/jpeg、image/png | 默认两种；前后端均按该模板限制 |
| template.maxFileSizeBytes | 整数 1～10485760 | 默认 5242880（5 MiB）；全局上限 10 MiB；字节等于上限允许 |
| upload.templateId | 有效模板 ID | 必选；确定服务端校验规则 |
| upload.file | multipart 单个文件 | 必选；拒绝空文件、类型不符或大小超限 |
| upload.originalName | 原始文件名文本 | 只保留展示，不用于拼接存储路径；清理路径分隔符和控制字符 |
| upload.detectedMimeType | image/jpeg / image/png | 服务端检查内容，不信任扩展名或浏览器 Content-Type；其他类型拒绝 |
| upload.slotIndex | 前端整数 0～requiredImageCount−1 | 控制槽位；最终以 assetIds 顺序提交，不信任上传结束先后顺序 |
| asset.id / previewUrl | 服务端 ID / 本人可访问文件 URL | 上传完成并校验成功后返回；blob URL 仅用于本地即时预览 |
| asset.sizeBytes | 正整数 | 后端实测；不得超过模板和全局限制 |
| asset.ownerId | 服务端会话身份 ID | 只读；不能请求传值覆盖 |
| asset.storageKey | 服务端生成的相对路径键 | 内部字段，不接受客户端路径，不直接暴露宿主机路径 |

建议服务端另设图片解码像素上限 20000000，拒绝无法识别、损坏或过大像素的图片。若后续采用不同上限，需同步文档和测试。模板约束在提交时再次检查；素材上传时通过不代表可用于任意模板。

## 6. 任务创建与模拟参数

| 参数 | 类型/可选值或范围 | 默认及规则 |
| --- | --- | --- |
| request.templateId | 有效模板 ID | 必选 |
| request.assetIds | 服务端素材 ID 的有序数组 | 长度必须等于 requiredImageCount；不得重复，不得引用他人或缺失素材 |
| request.simulation | 对象或省略 | 仅演示控制开启时接收；省略时使用后端默认配置 |
| simulation.failAtStage | NONE / MATERIAL_CHECK / MOCK_GENERATION / RESULT_ASSEMBLY | 默认 NONE；只允许一个失败阶段，不使用随机失败 |
| simulation.materialCheckDelayMs | 整数 0～30000 | 默认 1000；0 表示无额外等待，仍执行并记录该阶段 |
| simulation.mockGenerationDelayMs | 整数 0～30000 | 默认 4000 |
| simulation.resultAssemblyDelayMs | 整数 0～30000 | 默认 1000；三阶段最多配置 90000 ms，不含排队与文件/DB 操作 |
| task.id | 服务端生成不透明字符串 | 受理后返回，前端不得自行生成成功任务记录 |
| task.templateSnapshot | 结构化只读对象 | 创建时固定标题、素材约束和结果样本引用等；历史不随模板编辑而变化 |
| task.simulationSnapshot | 生效后的模拟配置对象 | 保存默认合并后的实际值，便于复现 |
| task.version | 整数 ≥ 0，状态更新递增 | 默认 0；前端拒绝用更小版本覆盖较新记录 |
| task.createdAt / updatedAt | UTC ISO 8601 时间 | 服务端生成；updatedAt 随持久化状态更新 |
| task.startedAt / finishedAt | UTC ISO 8601 时间或 null | 未开始/未结束为 null；终态必须有 finishedAt |
| task.result | null 或结果对象 | 只有 SUCCEEDED 非空，包含 id、url、mimeType、sizeBytes、createdAt |
| task.errorCode / errorMessage | null 或错误码/可读文本 | FAILED 时必有；不向用户泄露堆栈和物理路径 |

参数超界、非整数延迟、未知枚举、重复素材等在创建前返回 400，不创建“执行失败”任务。提交前验证通过后，素材在执行时丢失属于 MATERIAL_CHECK 执行失败。输入 ID 类型格式错误为 400，有效格式但资源缺失/不属于本人为 404。

建议演示组合：

| 目的 | failAtStage | 检查/生成/整理延迟 ms |
| --- | --- | --- |
| 成功流程 | NONE | 1000 / 4000 / 1000 |
| 素材阶段失败 | MATERIAL_CHECK | 1000 / 4000 / 1000 |
| 生成阶段失败 | MOCK_GENERATION | 1000 / 4000 / 1000 |
| 整理阶段失败 | RESULT_ASSEMBLY | 1000 / 4000 / 1000 |
| 观察多任务排队 | NONE | 2000 / 10000 / 2000；连续创建 3 个 |
| 快速测试 | NONE 或指定失败阶段 | 0 / 0 / 0；测试通过可控等待器验证阶段，不依赖肉眼观察 |

## 7. 查询、身份及响应参数

| 参数 | 类型/可选值或范围 | 默认及约束 |
| --- | --- | --- |
| session.demoKey | demo-a / demo-b | 不隐式默认身份；首次进入显示身份选择，Java 签发会话 |
| session.user | id、displayName | 服务端输出，客户端不得改写归属 |
| session.cookie | 服务端签名的 HttpOnly Cookie | 建议名 material_session；SameSite=Lax，Path=/；HTTPS 下 Secure=true |
| categoryId 查询 | 省略或有效分类 ID | 省略表示全部；“全部”不是实际分类记录 |
| status 查询 | 省略或 task.status 的非空子集 | 省略表示全部；多值使用重复参数，不接受任意 SQL 字符串 |
| page | 整数 ≥ 1 | 默认 1 |
| pageSize | 整数 1～50 | 默认 20；不合法返回 400，不静默截断 |
| history.sort | CREATED_AT_DESC | 当前固定排序，createdAt 相同时按 id DESC；不额外提供用户排序接口 |
| list.items / total | 数组 / 非负整数 | 空列表为 []、total=0，仍为成功响应 |
| task.steps | 固定三项有序数组 | 每项含 stage、status、startedAt、finishedAt、错误字段 |
| response.traceId | 服务端请求关联字符串 | 用于定位错误，不是用户输入 |

分页扫描活动任务期间集合可能变化：按 taskId 去重，当前已跟踪任务不因某页缺失直接删除；终态以详情查询确认，进入/刷新历史时重新协调活动任务集合。本期不实现取消或删除接口，因此无相关参数。

## 8. 错误码与 HTTP 状态

以下为建议的应用错误码集合，允许后续在保持错误类型分离的前提下补充；数据库/程序内部异常归为明确的安全兜底码，不直接拼接内部信息。

| HTTP / 作用域 | errorCode 可选值 | 处理方式 |
| --- | --- | --- |
| 400 参数校验 | VALIDATION_ERROR / INVALID_ASSET_COUNT / UNSUPPORTED_IMAGE_TYPE / INVALID_SIMULATION_CONFIG | 字段或表单提示；不创建任务 |
| 401 会话 | UNAUTHENTICATED | 重新选择演示身份，清理旧跟踪 |
| 403 功能限制 | SIMULATION_DISABLED | 不接受该环境的模拟控制 |
| 404 资源 | TEMPLATE_NOT_FOUND / ASSET_NOT_FOUND / TASK_NOT_FOUND / RESULT_NOT_FOUND | 不存在或无权限；不暴露他人记录 |
| 413 上传 | FILE_TOO_LARGE | 槽位错误，可更换图片 |
| 500/503 API 基础设施 | INTERNAL_ERROR / SERVICE_UNAVAILABLE / STORAGE_UNAVAILABLE | 请求失败；查询出错不等于任务失败 |
| 任务 FAILED，GET 本身仍为 200 | SIMULATED_STAGE_FAILURE / MATERIAL_UNAVAILABLE / GENERATION_FAILED / RESULT_WRITE_FAILED / RESTART_INTERRUPTED / EXECUTION_ERROR | 保存 errorMessage、阶段和步骤状态；展示执行失败 |

浏览器 NETWORK_ERROR、TIMEOUT、ABORTED 是请求侧原因，不是后端任务 errorCode。主动取消旧请求的 ABORTED 不显示为用户错误。

## 9. 运行配置与可选值

配置命名均为建议，实际实现时在 `.env.example` 和后端配置中对应并校验。不得在 Git 中提交真实密码、会话密钥或本机路径配置。

| 参数 | 可选值/范围 | 建议默认及用途 |
| --- | --- | --- |
| APP_MODE | DEMO / STANDARD | DEMO；STANDARD 下演示身份入口关闭且必须另有身份方案，本期不承诺可用生产登录 |
| SIMULATION_CONTROL_ENABLED | true / false | DEMO=true；其他环境建议 false |
| DEMO_SESSION_TTL_SECONDS | 整数 300～604800 | 86400（24 小时）；失效时 401，不影响已提交任务 |
| SESSION_SIGNING_SECRET | 部署方提供随机密钥，至少 32 字节 | 无可用于部署的固定默认；示例仅占位，缺失时启动失败 |
| JAVA_SERVER_PORT | 整数 1～65535 | 8080，容器内部端口 |
| NEXT_SERVER_PORT | 整数 1～65535 | 3000，默认浏览器入口 |
| MYSQL_PORT | 整数 1～65535 | 3306；Compose 内连接数据库服务，不必暴露宿主机 |
| MYSQL_HOST / MYSQL_DATABASE | 合法主机名 / 数据库名 | Compose 建议 mysql / material_studio |
| MYSQL_USER / MYSQL_PASSWORD | 非空字符串 | 独立应用用户、部署方密码；不使用 root 作为应用连接身份 |
| JAVA_INTERNAL_URL | 合法内部 HTTP(S) URL | Compose 建议 http://backend:8080；供 Next.js 代理使用，非浏览器公开变量 |
| STORAGE_ROOT | Java 可写绝对目录 | 容器建议 /app/data；挂载命名卷，不接受请求覆盖 |
| MAX_UPLOAD_FILE_BYTES | 整数 1～10485760 | 10485760；有效限制取该值与模板限制的较小值 |
| MAX_UPLOAD_REQUEST_BYTES | 整数，必须大于 MAX_UPLOAD_FILE_BYTES | 11534336（11 MiB）；预留 multipart 开销 |
| MAX_IMAGE_PIXELS | 正整数 | 20000000；解码前后检查图片尺寸 |
| TASK_WORKER_CONCURRENCY | 基线固定 1；扩展建议 2～4 | 1；改变值意味着选择有界线程池并补充认领并发测试 |
| TASK_DISPATCH_INTERVAL_MS | 整数 100～5000 | 500；工作线程空闲时扫描队列，与前端轮询无关 |
| TASK_DEFAULT_FAIL_AT_STAGE | NONE / 三个执行阶段 | NONE；仅作为未传 simulation 时的默认 |
| TASK_DEFAULT_CHECK_DELAY_MS | 整数 0～30000 | 1000 |
| TASK_DEFAULT_GENERATION_DELAY_MS | 整数 0～30000 | 4000 |
| TASK_DEFAULT_ASSEMBLY_DELAY_MS | 整数 0～30000 | 1000 |
| NEXT_PUBLIC_TASK_POLL_INTERVAL_MS | 整数 500～10000 | 1500；每任务上次请求结束后再等待该时长 |
| NEXT_PUBLIC_QUERY_TIMEOUT_MS | 整数 1000～30000 | 10000；只控制查询请求，不作为后台任务超时 |
| NEXT_PUBLIC_UPLOAD_TIMEOUT_MS | 整数 10000～120000 | 60000；超时仍需考虑服务端可能已保存素材 |
| NEXT_PUBLIC_SUBMIT_TIMEOUT_MS | 整数 3000～30000 | 10000；超时不自动重复 POST，先同步历史 |
| LOG_LEVEL | ERROR / WARN / INFO / DEBUG | INFO；DEBUG 仅本地诊断，日志不记录密码和会话密钥 |

模拟延迟和轮询时长用途不同：前者控制后端步骤，后者只控制读取频率。默认模拟失败阶段和三个默认延迟的配置变更仅影响之后受理的任务，旧任务继续使用快照。

## 10. 尚待核验与明确不提供的参数

- 具体依赖版本、CI action 版本、容器镜像版本：工程初始化前核验官方兼容性后固定，不使用 latest 作为交付版本。
- 模板示例图片路径、作者和许可：选择实际本地文件后登记来源，不将未知版权资源直接视为可分发素材。
- 不提供真实 AI 模型、提示词、付费密钥、视频格式、积分、会员、取消、重试、下载或删除参数；它们不在正文要求内。
- 本字典覆盖本方案对外字段、状态和主要运行配置；框架内部全部配置不属于业务参数，不假装能穷举。
