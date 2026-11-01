"use client";
/* eslint-disable @next/next/no-img-element -- Protected images use the browser session cookie. */
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api, type Asset, type Template } from "@/lib/api";
import { failedUpload, materialRequirement, rejectedSelection, uploadingSelection, type Slot } from "@/lib/upload-slot";
import { useRemote } from "./use-remote";
import { RequireSession, useSession, useTasks } from "./provider";
import { Feedback } from "./catalog";
import { TaskCard } from "./task-view";
import { PageHeading } from "@/components/ui/page-heading";

function UploadSlot({ index, template, disabled, onChange }: { index: number; template: Template; disabled: boolean; onChange: (index: number, slot: Slot) => void }) {
  const [slot, setSlot] = useState<Slot>({ uploading: false });
  const request = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement | null>(null);
  const update = (next: Slot) => { setSlot(next); onChange(index, next); };
  useEffect(() => () => request.current?.abort(), []);
  async function select(file?: File) {
    if (!file) return; request.current?.abort();
    const rejected = rejectedSelection(slot, file, template);
    if (rejected) { update(rejected); return; }
    const controller = new AbortController(); request.current = controller; const current = slot; update(uploadingSelection(current));
    const form = new FormData(); form.set("file", file); form.set("templateId", template.id);
    try {
      const asset = await api<Asset>("/api/assets", { method: "POST", body: form, signal: controller.signal }, 60000);
      if (!controller.signal.aborted) update({ asset, uploading: false });
    } catch (error) { if (!controller.signal.aborted) update(failedUpload(current, (error as Error).message)); }
  }
  return <div className="upload-slot"><div className="upload-frame">
    <button type="button" className="upload-preview upload-trigger" disabled={disabled} aria-label={`${slot.asset ? "替换" : "上传"}图片 ${index + 1}`} onClick={() => input.current?.click()}>
      {slot.asset ? <img src={slot.asset.previewUrl} alt={`素材 ${index + 1} 预览`} width={400} height={300} /> : <><span className="upload-plus" aria-hidden="true">＋</span><span className="upload-hint">单击上传文件</span></>}
    </button>
    {(slot.asset || slot.uploading) && <button type="button" className="upload-remove" aria-label={`移除图片 ${index + 1}`} title={`移除图片 ${index + 1}`} disabled={disabled} onClick={() => { request.current?.abort(); update({ uploading: false }); }}><span aria-hidden="true">×</span></button>}
    </div>
    <span className="upload-caption">图片 {index + 1}</span><input ref={input} hidden aria-label={`选择图片 ${index + 1}`} type="file" accept={template.acceptedMimeTypes.join(",")} disabled={disabled} onChange={event => { void select(event.target.files?.[0]); event.target.value = ""; }} />
    {slot.uploading && <p role="status">上传中…可以重新选择图片替换</p>}{slot.error && <p className="feedback" role="alert">{slot.error}</p>}
  </div>;
}
function AcceptedTask({ id }: { id: string }) {
  const { tasks, errors, tracker } = useTasks();
  const task = tasks[id];
  if (task) return <TaskCard task={task} />;
  if (errors[id]) return <div className="notice" data-task-id={id}><p>任务已受理。</p><p className="feedback" role="alert">查询暂不可用，任务没有被标为失败：{errors[id]} <button className="text-button" onClick={() => void tracker.query(id)}>恢复查询</button></p><Link href={`/tasks/${id}`}>查看任务</Link></div>;
  return <div className="notice" data-task-id={id} role="status"><p>任务已受理，正在读取状态。</p><Link href={`/tasks/${id}`}>查看任务 {id}</Link></div>;
}
function CreationForm({ template }: { template: Template }) {
  const [slots, setSlots] = useState<Slot[]>(() => Array.from({ length: template.requiredImageCount }, () => ({ uploading: false })));
  const [submitting, setSubmitting] = useState(false); const submitLock = useRef(false);
  const [error, setError] = useState<string | null>(null); const [accepted, setAccepted] = useState<string[]>([]);
  const [simulation, setSimulation] = useState(false); const [failAtStage, setFailAtStage] = useState("NONE");
  const [delays, setDelays] = useState([1000, 4000, 1000]); const { tracker } = useTasks();
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const uploading = slots.some(slot => slot.uploading);
  const ready = slots.every(slot => !!slot.asset && !slot.uploading);
  const phase = uploading ? "素材上传中" : submitting ? "提交请求中" : accepted.length > 0 ? "任务已受理，可以继续提交" : "";
  async function submit(event: React.FormEvent) {
    event.preventDefault(); if (!ready || submitLock.current) return;
    submitLock.current = true; setSubmitting(true); setError(null);
    try {
      const data = await api<{ taskId: string }>("/api/tasks", { method: "POST", body: JSON.stringify({ templateId: template.id, assetIds: slots.map(slot => slot.asset!.assetId), ...(simulation ? { simulation: { failAtStage, materialCheckDelayMs: delays[0], mockGenerationDelayMs: delays[1], resultAssemblyDelayMs: delays[2] } } : {}) }) });
      void tracker.query(data.taskId);
      if (mounted.current) setAccepted(ids => [data.taskId, ...ids]);
    } catch (cause) { if (mounted.current) { setError(`${(cause as Error).message} 如响应丢失，任务可能已受理，请先查看我的作品再决定是否重提。`); void tracker.start(); } }
    finally { submitLock.current = false; if (mounted.current) setSubmitting(false); }
  }
  return <><form onSubmit={event => void submit(event)} className="creation-panel"><h2>上传你的图片</h2><p className="muted">{materialRequirement(template)}</p>
    <div className="upload-grid">{slots.map((_, index) => <UploadSlot key={index} index={index} template={template} disabled={submitting} onChange={(i, slot) => setSlots(previous => previous.map((item, position) => position === i ? slot : item))} />)}</div>
    <details className="simulation"><summary>演示控制（可选）</summary><label><input type="checkbox" checked={simulation} disabled={submitting} onChange={event => setSimulation(event.target.checked)} />使用自定义模拟参数</label>{simulation && <div className="simulation-fields"><label>失败阶段<select value={failAtStage} disabled={submitting} onChange={event => setFailAtStage(event.target.value)}><option value="NONE">不失败</option><option value="MATERIAL_CHECK">素材检查</option><option value="MOCK_GENERATION">模拟生成</option><option value="RESULT_ASSEMBLY">结果整理</option></select></label>{["素材检查耗时", "模拟生成耗时", "结果整理耗时"].map((label, i) => <label key={label}>{label}（毫秒）<input type="number" min={0} max={30000} step={1} required disabled={submitting} value={Number.isNaN(delays[i]) ? "" : delays[i]} onChange={event => setDelays(values => values.map((value, j) => i === j ? event.target.valueAsNumber : value))} /></label>)}</div>}</details>
    {phase && <p role="status">{phase}</p>}
    {error && <p className="feedback" role="alert">{error} <Link href="/history">查看我的作品</Link></p>}
    <button className="button" type="submit" disabled={!ready || submitting}>{submitting ? "正在提交…" : "提交制作任务"}</button><p className="muted">受理后会立刻显示任务，提交按钮恢复可再次提交。每个任务单独跟踪。</p>
  </form><div className="submitted-tasks" aria-live="polite">{accepted.map(id => <AcceptedTask key={id} id={id} />)}</div></>;
}
export function TemplateDetail({ id }: { id: string }) {
  const { session } = useSession();
  const remote = useRemote<Template>(`/api/templates/${encodeURIComponent(id)}`, session.user?.id ?? "");
  if (remote.error) return <><PageHeading eyebrow="MAKE IT YOURS" title={remote.error.status === 404 ? "模板不存在" : "模板暂不可用"} description="返回模板列表，或稍后重试。" /><Feedback error={remote.error} retry={remote.retry} /><Link href="/templates">返回模板列表</Link></>;
  if (!remote.data) return <p role="status">正在加载模板详情…</p>;
  const template = remote.data;
  return <><PageHeading eyebrow="MAKE IT YOURS" title={template.title} description={template.description} /><p className="muted">{template.ownerId ? "这是你的自建模板，只有你能看到。" : "这是公共模板，所有人都能看到。"}</p><div className="detail-layout"><div className="template-example"><img src={template.coverUrl} alt={`${template.title} 示例`} width={800} height={600} /><p className="muted">示例效果 · 制作为模拟演示</p></div><RequireSession><CreationForm key={template.id} template={template} /></RequireSession></div></>;
}
