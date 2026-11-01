"use client";
/* eslint-disable @next/next/no-img-element -- Java serves local images; no remote optimizer or authentication forwarding is needed. */
import Link from "next/link";
import { useState } from "react";
import type { Category, Page, Template } from "@/lib/api";
import { useRemote } from "./use-remote";
import { useSession } from "./provider";
import { PageHeading } from "@/components/ui/page-heading";
import { EmptyState } from "@/components/ui/empty-state";

export function Feedback({ error, retry }: { error: Error; retry: () => void }) {
  return <div className="feedback" role="alert"><p>{error.message}</p><button className="button" onClick={retry}>重试</button></div>;
}
export function Catalog() {
  const [category, setCategory] = useState(""); const [scope, setScope] = useState("all"); const [page, setPage] = useState(1);
  const { session } = useSession();
  const categories = useRemote<Category[]>("/api/categories");
  const templates = useRemote<Page<Template>>(`/api/templates?page=${page}&pageSize=12&scope=${scope}${category ? `&categoryId=${encodeURIComponent(category)}` : ""}`, session.user?.id ?? "");
  return <><PageHeading eyebrow="EXPLORE YOUR INSPIRATION" title="每一份灵感，都值得成作。" description="公共模板所有人可见。选择身份后，还能看到只属于你的自建模板。" />
    <div className="category-tabs" aria-label="模板范围"><button aria-pressed={scope === "all"} onClick={() => { setScope("all"); setPage(1); }}>全部可见</button><button aria-pressed={scope === "public"} onClick={() => { setScope("public"); setPage(1); }}>公共模板</button><button aria-pressed={scope === "mine"} onClick={() => { setScope("mine"); setPage(1); }}>我的模板</button></div>
    {categories.error ? <Feedback error={categories.error} retry={categories.retry} /> : <div className="category-tabs" aria-label="模板分类"><button aria-pressed={!category} onClick={() => { setCategory(""); setPage(1); }}>全部模板</button>{categories.data?.map(item => <button key={item.id} aria-pressed={category === item.id} onClick={() => { setCategory(item.id); setPage(1); }}>{item.name}</button>)}</div>}
    {scope === "mine" && !session.loading && !session.user ? <EmptyState title="先登录" description="自建模板只对创建者可见。请先登录或注册，也可以在顶部选择演示用户 A 或 B。" /> : templates.error ? <Feedback error={templates.error} retry={templates.retry} /> : !templates.data ? <p role="status">正在加载模板…</p> : templates.data.items.length === 0 ? <EmptyState title="暂无模板" description={scope === "mine" ? "你还没有创建模板。" : "这个范围还没有可用模板，试试其他分类。"} /> : <>
      <div className="template-grid">{templates.data.items.map(template => <Link className="template-card" href={`/templates/${template.id}`} key={template.id}><img src={template.coverUrl} alt={template.title} width={800} height={600} /><div><h2>{template.title}</h2><p>{template.description}</p><span>{template.ownerId ? "我的模板" : "公共模板"} · 需要 {template.requiredImageCount} 张图片 <span aria-hidden="true">↗</span></span></div></Link>)}</div>
      <div className="pagination"><button disabled={page === 1} onClick={() => setPage(n => n - 1)}>上一页</button><span>第 {page} 页 · 共 {templates.data.total} 个模板</span><button disabled={page * 12 >= templates.data.total} onClick={() => setPage(n => n + 1)}>下一页</button></div>
    </>}
  </>;
}
