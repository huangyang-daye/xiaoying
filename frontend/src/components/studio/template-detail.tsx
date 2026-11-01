"use client";
/* eslint-disable @next/next/no-img-element -- Protected images use the browser session cookie. */
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { api, type Asset, type Template } from "@/lib/api";
import { useRemote } from "./use-remote";
import { RequireSession, useTasks } from "./provider";
import { Feedback } from "./catalog";
import { TaskCard } from "./task-view";
import { PageHeading } from "@/components/ui/page-heading";

type Slot = { asset?: Asset; uploading: boolean; error?: string };
function UploadSlot({ index, template, disabled, onChange }: { index: number; template: Template; disabled: boolean; onChange: (index: number, slot: Slot) => void }) {
  const [slot, setSlot] = useState<Slot>({ uploading: false });
  const request = useRef<AbortController | null>(null);
  const input = useRef<HTMLInputElement | null>(null);
  const update = (next: Slot) => { setSlot(next); onChange(index, next); };
  useEffect(() => () => request.current?.abort(), []);
  async function select(file?: File) {
    if (!file) return; request.current?.abort();
    if (!template.acceptedMimeTypes.includes(file.type)) { update({ uploading: false, error: "请选择 JPEG 或 PNG 图片。" }); return; }
    if (file.size === 0 || file.size > template.maxFileSizeBytes) { update({ uploading: false, error: `图片须大于 0，且不超过 ${template.maxFileSizeBytes / 1048576} MiB。` }); return; }
    const controller = new AbortController(); request.current = controller; update({ uploading: true });
    const form = new FormData(); form.set("file", file); form.set("templateId", template.id);
    try {
      const asset = await api<Asset>("/api/assets", { method: "POST", body: form, signal: controller.signal }, 60000);
      if (!controller.signal.aborted) update({ asset, uploading: false });
    } catch (error) { if (!controller.signal.aborted) update({ uploading: false, error: (error as Error).message }); }
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
function CreationForm({ template }: { template: Template }) {
  const [slots, setSlots] = useState<Slot[]>(() => Array.from({ length: template.requiredImageCount }, () => ({ uploading: false })));
  const [submitting, setSubmitting] = useState(false); const submitLock = useRef(false);
  const [error, setError] = useState<string | null>(null); const [accepted, setAccepted] = useState<string[]>([]);
  const [simulation, setSimulation] = useState(false); const [failAtStage, setFailAtStage] = useState("NONE");
  const [delays, setDelays] = useState([1000, 4000, 1000]); const { tasks, tracker } = useTasks();
  const lifetime = useRef<AbortController | null>(null);
  useEffect(() => { lifetime.current = new AbortController(); return () => lifetime.current?.abort(); }, []);
  const ready = slots.every(slot => !!slot.asset && !slot.uploading);
  async function submit(event: React.FormEvent) {
    event.preventDefault(); if (!ready || submitLock.current) return;
    submitLock.current = true; setSubmitting(true); setError(null); const signal = lifetime.current!.signal;
    try {
      const data = await api<{ taskId: string }>("/api/tasks", { method: "POST", signal, body: JSON.stringify({ templateId: template.id, assetIds: slots.map(slot => slot.asset!.assetId), ...(simulation ? { simulation: { failAtStage, materialCheckDelayMs: delays[0], mockGenerationDelayMs: delays[1], resultAssemblyDelayMs: delays[2] } } : {}) }) });
      if (signal.aborted) return; setAccepted(ids => [data.taskId, ...ids]); void tracker.query(data.taskId);
    } catch (cause) { if (!signal.aborted) { setError(`${(cause as Error).message} 如响应丢失，任务可能已受理，请先查看我的作品再决定是否重提。`); void tracker.start(); } }
    finally { if (!signal.aborted) { submitLock.current = false; setSubmitting(false); } }
  }
  return <><form onSubmit={event => void submit(event)} className="creation-panel"><h2>上传你的图片</h2><p className="muted">需要 {template.requiredImageCount} 张 · JPEG / PNG · 每张不超过 {template.maxFileSizeBytes / 1048576} MiB</p>
    <div className="upload-grid">{slots.map((_, index) => <UploadSlot key={index} index={index} template={template} disabled={submitting} onChange={(i, slot) => setSlots(previous => previous.map((item, position) => position === i ? slot : item))} />)}</div>
    <details className="simulation"><summary>演示控制（可选）</summary><label><input type="checkbox" checked={simulation} disabled={submitting} onChange={event => setSimulation(event.target.checked)} />使用自定义模拟参数</label>{simulation && <div className="simulation-fields"><label>失败阶段<select value={failAtStage} disabled={submitting} onChange={event => setFailAtStage(event.target.value)}><option value="NONE">不失败</option><option value="MATERIAL_CHECK">素材检查</option><option value="MOCK_GENERATION">模拟生成</option><option value="RESULT_ASSEMBLY">结果整理</option></select></label>{["素材检查耗时", "模拟生成耗时", "结果整理耗时"].map((label, i) => <label key={label}>{label}（毫秒）<input type="number" min={0} max={30000} step={1} required disabled={submitting} value={Number.isNaN(delays[i]) ? "" : delays[i]} onChange={event => setDelays(values => values.map((value, j) => i === j ? event.target.valueAsNumber : value))} /></label>)}</div>}</details>
    {error && <p className="feedback" role="alert">{error} <Link href="/history">查看我的作品</Link></p>}
    <button className="button" type="submit" disabled={!ready || submitting}>{submitting ? "正在提交…" : "提交制作任务"}</button><p className="muted">任务受理后，你可以继续选择模板或提交其他任务。</p>
  </form><div className="submitted-tasks" aria-live="polite">{accepted.map(id => tasks[id] ? <TaskCard key={id} task={tasks[id]} /> : <div className="notice" key={id}>任务已受理。<Link href={`/tasks/${id}`}>查看任务 {id}</Link></div>)}</div></>;
}
export function TemplateDetail({ id }: { id: string }) {
  const remote = useRemote<Template>(`/api/templates/${encodeURIComponent(id)}`);
  if (remote.error) return <><PageHeading eyebrow="MAKE IT YOURS" title={remote.error.status === 404 ? "模板不存在" : "模板暂不可用"} description="返回模板列表，或稍后重试。" /><Feedback error={remote.error} retry={remote.retry} /><Link href="/templates">返回模板列表</Link></>;
  if (!remote.data) return <p role="status">正在加载模板详情…</p>;
  const template = remote.data;
  return <><PageHeading eyebrow="MAKE IT YOURS" title={template.title} description={template.description} /><div className="detail-layout"><div className="template-example"><img src={template.coverUrl} alt={`${template.title} 示例`} width={800} height={600} /><p className="muted">示例效果 · 制作为模拟演示</p></div><RequireSession><CreationForm key={template.id} template={template} /></RequireSession></div></>;
}
