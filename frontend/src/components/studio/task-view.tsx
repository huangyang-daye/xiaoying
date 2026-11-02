"use client";
/* eslint-disable @next/next/no-img-element -- Personal result images must use the browser's session cookie. */
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { statusText, stageText, terminal, type Task, type Page } from "@/lib/api";
import { useTasks, RequireSession, useSession } from "./provider";
import { useRemote } from "./use-remote";
import { Feedback } from "./catalog";
import { PageHeading } from "@/components/ui/page-heading";
import { EmptyState } from "@/components/ui/empty-state";

function Preview({ url, title, close }: { url: string; title: string; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { const element = dialog.current!; element.showModal(); return () => element.close(); }, []);
  return <dialog ref={dialog} className="preview-dialog" aria-label="作品图片预览" onCancel={close}><button autoFocus className="button" onClick={close}>关闭预览</button><img src={url} alt={`${title} 完整结果`} width={800} height={600} /></dialog>;
}
function ResultImage({ task }: { task: Task }) {
  const [preview, setPreview] = useState(false); const [failed, setFailed] = useState(false); const [attempt, setAttempt] = useState(0);
  if (!task.result) return null;
  return <>{failed ? <p role="alert">图片加载失败 <button onClick={() => { setFailed(false); setAttempt(n => n + 1); }}>重新加载图片</button></p> : <button className="image-button" aria-label={`预览 ${task.templateTitle} 的结果`} onClick={() => setPreview(true)}><img key={attempt} src={`${task.result.url}?preview=${attempt}`} alt={`${task.templateTitle} 制作结果`} width={800} height={600} onError={() => setFailed(true)} /></button>}
    {preview && <Preview url={task.result.url} title={task.templateTitle} close={() => setPreview(false)} />}
  </>;
}
export function TaskCard({ task, detailed = false }: { task: Task; detailed?: boolean }) {
  const { errors, tracker } = useTasks();
  return <article className="task-card" data-task-id={task.id}>
    <div className="task-heading"><Link href={`/tasks/${task.id}`}><h2>{task.templateTitle}</h2></Link><span className={`status-badge status-${task.status}`}>{statusText[task.status]}</span></div>
    <p className="muted"><time dateTime={task.createdAt}>{new Date(task.createdAt).toLocaleString("zh-CN")}</time><br /><small>任务 {task.id}</small></p>
    <p aria-live="polite">{stageText[task.currentStage]}</p>
    {(task.attemptCount ?? 0) > 0 && <p className="muted">执行尝试 {task.attemptCount} / {task.maxAttempts}{task.status === "QUEUED" && <> · 等待自动恢复/重试{task.nextAttemptAt && <>，预计 {new Date(task.nextAttemptAt).toLocaleTimeString("zh-CN")}</>}</>}{task.lastErrorMessage && <> · 上次执行：{task.lastErrorMessage}</>}</p>}
    {task.status === "FAILED" && <p className="feedback">{task.failureStage && `${stageText[task.failureStage]}：`}{task.errorMessage}</p>}
    {!terminal(task) && !errors[task.id] && <p className="muted">正在自动跟踪，完成后停止查询。</p>}
    {errors[task.id] && <p className="feedback" role="alert">查询暂不可用，保留上次状态：{errors[task.id]} <button className="text-button" onClick={() => void tracker.query(task.id)}>恢复查询</button></p>}
    {detailed && <ol className="step-list">{task.steps.map(step => <li key={step.stage}><span>{stageText[step.stage]}</span><span>{({ PENDING: "待执行", RUNNING: "执行中", SUCCEEDED: "已完成", FAILED: "失败", SKIPPED: "已跳过" } as Record<string, string>)[step.status]}</span></li>)}</ol>}
    {detailed && task.attempts && task.attempts.length > 0 && <details><summary>执行尝试记录</summary><ol>{task.attempts.map(attempt => <li key={attempt.attemptNo}>第 {attempt.attemptNo} 次 · {stageText[attempt.stage]} · {({ RUNNING: "执行中", SUCCEEDED: "成功", FAILED: "最终失败", RETRY_SCHEDULED: "已安排重试", LEASE_EXPIRED: "中断后恢复" } as Record<string, string>)[attempt.outcome]}{attempt.errorMessage && `：${attempt.errorMessage}`}</li>)}</ol></details>}
    <ResultImage task={task} />
    {!detailed && <Link className="text-button" href={`/tasks/${task.id}`}>查看制作详情 ↗</Link>}
  </article>;
}
function HistoryContent() {
  const { session } = useSession();
  const [page, setPage] = useState(1);
  const remote = useRemote<Page<Task>>(`/api/tasks?page=${page}&pageSize=12`, session.user?.id ?? "");
  const { tasks, tracker } = useTasks();
  useEffect(() => { remote.data?.items.forEach(tracker.merge); }, [remote.data, tracker]);
  const listed = remote.data?.items ?? [];
  const extra = page === 1 ? Object.values(tasks).filter(task => !listed.some(item => item.id === task.id) && (listed.length === 0 || task.createdAt >= listed[0].createdAt)) : [];
  const items = [...extra, ...listed.map(task => tasks[task.id] ?? task)].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const refresh = () => { remote.retry(); void tracker.start(); };
  return <><p className="muted">当前账户 {session.user?.displayName}（{session.user?.username}）。<Link href="/account">个人设置</Link>可以更改昵称、头像和密码。</p><div className="section-heading"><p>刷新后从服务器恢复记录，并继续跟踪未完成的任务。</p><button className="button" onClick={refresh}>刷新记录</button></div>
    {remote.error && <Feedback error={remote.error} retry={refresh} />}
    {!remote.data && !remote.error && items.length === 0 ? <p role="status">正在读取个人记录…</p> : null}
    {remote.data && items.length === 0 ? <EmptyState title="还没有作品" description="选择喜欢的模板，开始第一次创作吧。" action={{ href: "/templates", label: "探索模板" }} /> : null}
    {items.length > 0 && <div className="task-grid">{items.map(task => <TaskCard key={task.id} task={task} />)}</div>}
    {remote.data && <div className="pagination"><button disabled={page === 1} onClick={() => setPage(n => n - 1)}>上一页</button><span>第 {page} 页 · 数据库共 {remote.data.total} 条记录</span><button disabled={page * 12 >= remote.data.total} onClick={() => setPage(n => n + 1)}>下一页</button></div>}
  </>;
}
export function History() { return <><PageHeading eyebrow="YOUR CREATIVE COLLECTION" title="留住每一次灵感。" description="制作进度、历史记录与完成的作品，都在这里。" /><RequireSession><HistoryContent /></RequireSession></>; }
function DetailContent({ id }: { id: string }) {
  const { tasks, errors, tracker } = useTasks();
  useEffect(() => { let current = true; queueMicrotask(() => { if (current) void tracker.query(id); }); return () => { current = false; }; }, [id, tracker]);
  if (tasks[id]) return <TaskCard task={tasks[id]} detailed />;
  if (errors[id]?.includes("不存在")) return <div className="notice"><h2>任务不存在</h2><p>这条记录不属于当前身份，或任务编号无效。</p><Link href="/history">返回我的作品</Link></div>;
  if (errors[id]) return <div className="notice" role="alert"><p>查询暂不可用，任务没有被标为失败：{errors[id]}</p><button className="button" onClick={() => void tracker.query(id)}>恢复查询</button></div>;
  return <p role="status">正在读取任务…</p>;
}
export function TaskDetail({ id }: { id: string }) { return <><PageHeading eyebrow="FROM IDEA TO CREATION" title="见证灵感的诞生。" description="你可以继续浏览网站，制作会在后台进行。" /><RequireSession><DetailContent id={id} /></RequireSession></>; }
