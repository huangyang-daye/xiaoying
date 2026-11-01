# 映作前端

Node.js 24.22.1、Next.js 16.4.3 App Router、React 19.3.1、TypeScript 5.9.3，已接入 Java 后端业务接口。支持模板分类与详情、A/B 演示身份、图片上传/替换/移除、异步任务提交、个人历史、三阶段进度、失败原因和图片预览。

## 启动

推荐在仓库根目录复制 `.env.example` 为 `.env`，修改本地数据库密码，然后执行：

```sh
docker compose up -d --build --wait
```

访问 http://localhost:3000。该命令启动 Next.js、Java 和 MySQL。修改 FRONTEND_PORT 后，需要把实际浏览器 origin 加入 ALLOWED_ORIGINS；浏览器 API 和图片都通过 Next.js 转发至 Java。

本地开发可只启动后端依赖：

```sh
# 仓库根目录
docker compose up -d --build mysql backend
# frontend 目录，开发与测试使用 Node.js 24
npm ci
npm run dev
```

本地默认 Java 地址为 http://localhost:8080。按需复制 `frontend/.env.example` 为 `.env.local` 修改 JAVA_INTERNAL_URL。它仅供服务端使用，不加 NEXT_PUBLIC_，变更后重启开发服务，生产环境则重新构建。容器构建固定使用 http://backend:8080。

## 操作与边界

1. 顶部选择演示用户 A 或 B。两个身份的数据隔离；它们是公开的演示身份，不是生产登录系统。
2. 按分类选择模板，上传规定数量的 JPEG/PNG；前后端同时校验数量、类型和大小，只有服务端上传成功的 assetId 才能提交。
3. 可以展开“演示控制”，指定三个步骤各自 0～30000 ms 的耗时，以及不失败、素材检查失败、模拟生成失败或结果整理失败。未勾选自定义参数时使用后端默认值。
4. 收到 taskId 后提交状态结束，可以继续提交或切换页面。顶部提示活动任务；我的作品、任务详情和提交反馈共享同一任务数据。
5. 成功后点击图片预览；失败显示服务端原因。查询网络异常仅暂停该任务查询、保留原有业务状态，点击“恢复查询”继续。

默认每个未完成任务在上一次请求结束后等待 1500 ms 再查询；完成后停止，没有活动任务时没有轮询。刷新从 Java 读取当前会话、历史和全部活动任务页，前端缓存不是历史持久化来源。切换身份会卸载并清理旧请求、计时器和上传状态；跨标签页切换会话会通知其他标签重新同步。

提交请求超时不会自动重发 POST，用户应先检查历史，避免重复制作。服务重启后需要重新选择演示身份，已结束历史仍由 MySQL 恢复；后台运行任务的重启策略见后端说明。未被任务使用的上传素材回收尚未实现。

## 代码组织

- src/app：App Router 页面、布局、错误/404和样式。
- src/components/studio：会话 Provider、模板、上传、历史和任务详情。
- src/lib/api.ts：Java 响应类型、请求超时与 API 错误。
- src/lib/task-tracker.ts：按任务 ID 独立跟踪、停止轮询、异常恢复和过期响应隔离。
- e2e：通过真实 Java/MySQL 的 Playwright 联调场景。

图片直接通过 img 与浏览器 Cookie 读取 Java 受保护资源，避免图片优化代理丢失会话。预览使用原生模态 dialog，支持 Escape 和焦点恢复。字体为系统字体，示例图片由 Java 生成。

## 检查

```sh
npm run lint
npm run typecheck
npm test
npm run build
npm start
```

版本固定在 package.json 和 package-lock.json。运行浏览器联调前先启动整站；Windows 默认使用已安装的 Edge，Linux 执行 `npx playwright install --with-deps chromium` 后使用 Chromium：

```sh
npm run test:e2e
# 如使用不同地址，设置 PLAYWRIGHT_BASE_URL
```

CI 的 frontend-ci 执行 lint、类型、状态测试和构建，backend-ci 执行 Java 测试，integration-ci 构建整站容器后执行真实浏览器流程；本地通过不代表 GitHub 托管流水线已经执行。

2026-11-01 已通过 4 项状态测试、2 项真实 Java/MySQL 浏览器场景、lint、类型检查和生产容器构建。随后对实现版本 `6b7465d` 的容器复核再次通过上述检查和两个 Chromium 浏览器场景，详见[根目录验证结果](../README.md#构建与测试)；此前具体覆盖与边界见 [联调验收记录](../docs/integration-validation.md)。Next.js 运行时最低 Node 20.9，但本项目开发测试依赖要求较新 Node，统一使用 Node 24 和 `.nvmrc` 指定版本。

## Windows 文件占用

重新执行 npm ci 前，先在本项目的 dev/start 终端按 Ctrl+C 停止服务，避免 next-swc 原生模块被占用。清理中断可能导致 next 命令缺失，停止服务后重新 npm ci 即可。无需终止其他项目的 Node 进程。

用户级 npm 配置的 home 弃用提示与文件占用无关。现有 ESLint 9 及其插件兼容限制、间接 minimatch 审计告警仍待上游兼容版本更新，不通过强制升级跨越插件 peer 范围。

## AI 和素材来源

使用 AI 辅助核对 Java 契约、拆分会话/上传/任务状态、编写页面与测试，再通过 lint、类型检查、状态测试及容器联调修正问题。样式和图形沿用本仓库代码，业务数据和示例图片来自 Java。当前仍为本地演示方案，没有生产认证、配额、支付或真实 AI 服务。
