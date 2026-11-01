# 素材制作网站

一个可独立运行的素材制作网站。用户可以浏览模板、上传图片并提交制作任务，在任务后台处理期间继续使用网站，随后查看自己的历史记录和制作结果。

## 当前阶段

已完成 Git 初始化、任务规划、前端框架和后端基本业务。`backend/` 提供模板、演示身份、上传、三阶段异步任务、历史与图片结果，数据通过 MyBatis 持久化到 MySQL。`frontend/` 已接入业务接口，支持身份选择、模板浏览、上传制作、多任务跟踪、历史与结果预览。

进入 `frontend/`，使用 Node.js 24（推荐 24.22.1）运行 `npm ci`、`npm run dev`，访问 `http://localhost:3000`。检查命令和配置详见 [前端说明](frontend/README.md)。

后端推荐在根目录复制 `.env.example` 为 `.env`，设置本地数据库密码后执行 `docker compose up -d --build`，访问 `http://localhost:8080/actuator/health`。此 Compose 启动 Next.js、Java 和 MySQL，网站入口为 `http://localhost:3000`；数据库宿主机端口默认 13306。接口、模拟演示、测试和配置见 [后端说明](backend/README.md)。

## 技术栈

| 层次 | 技术与职责 |
| --- | --- |
| 前端 | Next.js App Router、React、TypeScript；提供网站页面与交互 |
| 后端 | Java、Spring Boot、MyBatis；提供模板、素材、制作任务和个人记录相关业务接口 |
| 数据库 | MySQL；持久化任务状态、历史记录及相关业务数据 |

Next.js 可以直接调用 Java 后端，也可以提供接口中间层。业务执行和持久化必须由 Java 后端承担，不能仅由 Next.js 完成。

## 预期使用流程

1. 用户浏览并选择模板。
2. 用户上传图片，提交制作任务。
3. 后端接受任务并在后台处理，用户可以继续使用网站。
4. 用户查看任务进度、个人历史记录和制作结果。

## 后续代码组织

采用同一仓库管理前后端：

```text
frontend/   Next.js App Router 前端（已对接 Java）
backend/    Spring Boot + MyBatis 后端、Flyway 迁移与测试
compose.yaml 前端、Java 与 MySQL 容器启动配置
docs/       项目范围和架构说明
```

系统边界、任务处理原则及待确认事项见 [架构说明](docs/architecture.md)。

## 任务规划

根据 [任务要求](任务要求.md) 整理的 [实现方案](docs/implementation-plan.md) 包含 R-01～R-14 对应目标、接口与数据模型草案、实施顺序及验收场景；[参数字典](docs/parameter-catalog.md) 列出状态枚举、业务字段、模拟控制、架构选项及运行配置的候选值和建议默认值。

上述文档保留规划时的方案与候选参数；当前已实现范围和实际接口以各工程 README 及源码为准。

## Git

主分支为 `main`。本地仓库与远程托管分开配置；当前远程地址可通过 `git remote -v` 查看。
