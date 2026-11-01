"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { api, type Category, type Page, type Template } from "@/lib/api";
import { PageHeading } from "@/components/ui/page-heading";
import { RequireSession, useSession } from "./provider";
import { Feedback } from "./catalog";
import { useRemote } from "./use-remote";

type Draft = {
  id: string; categoryId: string; title: string; description: string; requiredImageCount: string;
  jpeg: boolean; png: boolean; maxFileSizeBytes: string; coverUrl: string; sampleResultUrl: string; mode: "create" | "edit";
};

function blank(categoryId = ""): Draft {
  return { id: "", categoryId, title: "", description: "", requiredImageCount: "1", jpeg: true, png: true, maxFileSizeBytes: "5242880", coverUrl: "", sampleResultUrl: "", mode: "create" };
}
function fromTemplate(template: Template): Draft {
  return {
    id: template.id, categoryId: template.categoryId, title: template.title, description: template.description,
    requiredImageCount: String(template.requiredImageCount), jpeg: template.acceptedMimeTypes.includes("image/jpeg"),
    png: template.acceptedMimeTypes.includes("image/png"), maxFileSizeBytes: String(template.maxFileSizeBytes),
    coverUrl: template.storedCoverUrl ?? "", sampleResultUrl: template.sampleResultUrl ?? "", mode: "edit",
  };
}
function problem(draft: Draft) {
  if (draft.mode === "create" && (draft.id.trim() === "manage" || !/^[a-z0-9][a-z0-9-]{0,62}$/.test(draft.id.trim()))) return "模板编号须为 1 至 63 位小写字母、数字或连字符，并以字母或数字开头";
  if (!draft.title.trim() || draft.title.trim().length > 80) return "标题须为 1 至 80 个字符";
  if (draft.description.trim().length > 1000) return "描述不能超过 1000 个字符";
  if (!draft.categoryId) return "请选择已有分类";
  if (!/^[1-3]$/.test(draft.requiredImageCount)) return "所需图片数量须为 1 至 3";
  if (!draft.jpeg && !draft.png) return "请至少选择一种图片类型";
  if (!/^\d+$/.test(draft.maxFileSizeBytes) || Number(draft.maxFileSizeBytes) < 1 || Number(draft.maxFileSizeBytes) > 10485760) return "单张图片大小上限须为 1 至 10485760 字节";
  if (draft.coverUrl.trim().length > 2048 || draft.sampleResultUrl.trim().length > 2048) return "图片链接不能超过 2048 个字符";
  return "";
}

export function TemplateManager() {
  const { session } = useSession();
  const categories = useRemote<Category[]>("/api/categories");
  const [page, setPage] = useState(1);
  const [scope, setScope] = useState("all");
  const [revision, setRevision] = useState(0);
  const templates = useRemote<Page<Template>>(`/api/templates?page=${page}&pageSize=20&scope=${scope}&revision=${revision}`, session.user?.id ?? "");
  const [draft, setDraft] = useState<Draft>(blank());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState<Template | null>(null);
  const form = useRef<HTMLFormElement>(null);
  const patch = (next: Partial<Draft>) => { setDraft(current => ({ ...current, ...next })); setSaved(null); };
  const edit = (template: Template) => { setDraft(fromTemplate(template)); setError(""); setSaved(null); form.current?.scrollIntoView({ block: "start" }); };

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const message = problem(draft);
    if (message) { setError(message); return; }
    const payload = {
      id: draft.id.trim(), categoryId: draft.categoryId, title: draft.title.trim(), description: draft.description.trim(),
      requiredImageCount: Number(draft.requiredImageCount), maxFileSizeBytes: Number(draft.maxFileSizeBytes),
      acceptedMimeTypes: [draft.jpeg ? "image/jpeg" : "", draft.png ? "image/png" : ""].filter(Boolean),
      coverUrl: draft.coverUrl.trim(), sampleResultUrl: draft.sampleResultUrl.trim(),
    };
    setSaving(true); setError("");
    try {
      const result = await api<Template>(draft.mode === "create" ? "/api/templates" : `/api/templates/${encodeURIComponent(draft.id)}`, {
        method: draft.mode === "create" ? "POST" : "PUT", body: JSON.stringify(payload),
      });
      setDraft(fromTemplate(result)); setSaved(result); setRevision(value => value + 1);
    } catch (reason) { setError((reason as Error).message); }
    finally { setSaving(false); }
  }

  return <>
    <PageHeading eyebrow="TEMPLATE LIBRARY" title="管理你的模板。" description="新建的模板只有创建者可见。公共模板所有人都可以浏览，登录后也可以修改。" />
    <div className="manage-layout">
      <RequireSession>
        <form className="editor-card" ref={form} onSubmit={event => void submit(event)}>
          <h2>{draft.mode === "create" ? "新建模板" : `修改 ${draft.title || draft.id}`}</h2>
          <div className="editor-fields">
            <label>模板编号<input value={draft.id} disabled={draft.mode === "edit" || saving} autoComplete="off" onChange={event => patch({ id: event.target.value })} />
              <span className="muted">创建后不可修改，且只有你能看到。只能使用小写字母、数字和连字符。</span></label>
            <label>分类<select value={draft.categoryId} disabled={saving} onChange={event => patch({ categoryId: event.target.value })}>
              <option value="">{categories.data ? "请选择分类" : "正在加载分类…"}</option>
              {categories.data?.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
            </select></label>
            <label>标题<input value={draft.title} maxLength={80} disabled={saving} onChange={event => patch({ title: event.target.value })} /></label>
            <label>描述<textarea value={draft.description} maxLength={1000} disabled={saving} onChange={event => patch({ description: event.target.value })} /></label>
            <label>所需图片数量<input inputMode="numeric" value={draft.requiredImageCount} disabled={saving} onChange={event => patch({ requiredImageCount: event.target.value })} /></label>
            <fieldset className="mime-field"><legend>接受的图片类型</legend>
              <div className="check-row">
                <label><input type="checkbox" checked={draft.jpeg} disabled={saving} onChange={event => patch({ jpeg: event.target.checked })} />JPEG</label>
                <label><input type="checkbox" checked={draft.png} disabled={saving} onChange={event => patch({ png: event.target.checked })} />PNG</label>
              </div>
            </fieldset>
            <label>单张大小上限（字节）<input inputMode="numeric" value={draft.maxFileSizeBytes} disabled={saving} onChange={event => patch({ maxFileSizeBytes: event.target.value })} />
              <span className="muted">1 至 10485760。5 MiB 等于 5242880。</span></label>
            <label>封面链接<input value={draft.coverUrl} disabled={saving} placeholder="留空则使用自动生成的示例图" onChange={event => patch({ coverUrl: event.target.value })} /></label>
            <label>模拟结果图链接<input value={draft.sampleResultUrl} disabled={saving} placeholder="留空则使用自动生成的示例图" onChange={event => patch({ sampleResultUrl: event.target.value })} /></label>
          </div>
          {error && <p className="feedback" role="alert">{error}</p>}
          {saved && <p role="status">已保存「{saved.title}」。封面{saved.storedCoverUrl ? "使用填写的链接" : "使用自动示例图"}。<Link href={`/templates/${saved.id}`}>查看模板</Link></p>}
          <div className="editor-actions">
            <button className="button" type="submit" disabled={saving}>{saving ? "正在保存…" : draft.mode === "create" ? "添加模板" : "保存修改"}</button>
            <button className="text-button" type="button" disabled={saving} onClick={() => { setDraft(blank(categories.data?.[0]?.id ?? "")); setError(""); setSaved(null); }}>改为新建</button>
          </div>
        </form>
      </RequireSession>
      <section className="manage-list" aria-label="已有模板">
        <h2>已有模板</h2>
        <div className="category-tabs" aria-label="模板范围"><button type="button" aria-pressed={scope === "all"} onClick={() => { setScope("all"); setPage(1); }}>全部可见</button><button type="button" aria-pressed={scope === "public"} onClick={() => { setScope("public"); setPage(1); }}>公共模板</button><button type="button" aria-pressed={scope === "mine"} onClick={() => { setScope("mine"); setPage(1); }}>我的模板</button></div>
        {scope === "mine" && !session.loading && !session.user ? <p className="muted">请先登录或选择演示身份，才能查看自建模板。</p> : templates.error ? <Feedback error={templates.error} retry={templates.retry} /> : !templates.data ? <p role="status">正在加载模板…</p> : templates.data.items.length === 0 ? <p className="muted">{scope === "mine" ? "你还没有创建模板。" : "这个范围还没有模板。"}</p> : <>
          {templates.data.items.map(template => <div className="manage-item" key={template.id}><div><h3>{template.title}</h3><p className="muted">{template.ownerId ? "我的模板" : "公共模板"} · {template.id} · 需要 {template.requiredImageCount} 张图片{template.storedCoverUrl || template.sampleResultUrl ? " · 含自定义图片链接" : ""}</p></div><button className="button" type="button" onClick={() => edit(template)}>修改</button></div>)}
          <div className="pagination"><button type="button" disabled={page === 1} onClick={() => setPage(value => value - 1)}>上一页</button><span>第 {page} 页 · 共 {templates.data.total} 个</span><button type="button" disabled={page * 20 >= templates.data.total} onClick={() => setPage(value => value + 1)}>下一页</button></div>
        </>}
      </section>
    </div>
  </>;
}
