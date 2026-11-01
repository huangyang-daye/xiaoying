"use client";
/* eslint-disable @next/next/no-img-element -- Avatar and result images use the session cookie. */
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api, type Page, type Task, type User } from "@/lib/api";
import { PageHeading } from "@/components/ui/page-heading";
import { RequireSession, useSession } from "./provider";
import { useRemote } from "./use-remote";
import { Feedback } from "./catalog";
import { TaskCard } from "./task-view";

export function AccountSettings() {
  return <>
    <PageHeading eyebrow="YOUR STUDIO" title="个人设置与创作记录。" description="这里展示你的资料和最近作品。点击「编辑信息」修改昵称和密码，点击头像更换头像。删除账户在页面最下方。" />
    <RequireSession><AccountContent /></RequireSession>
  </>;
}
function AccountContent() {
  const { session, replaceUser } = useSession();
  const user = session.user!;
  const records = useRemote<Page<Task>>("/api/tasks?page=1&pageSize=3", user.id);
  const [editing, setEditing] = useState(false);
  const [avatarOpen, setAvatarOpen] = useState(false);
  return <div className="account-layout">
    <section className="editor-card profile-head">
      <div className="profile-main">
        <button type="button" className="avatar-button" aria-haspopup="dialog" aria-label="修改头像" onClick={() => setAvatarOpen(true)}>
          {user.avatarUrl ? <img className="profile-avatar" src={user.avatarUrl} alt="" width={72} height={72} /> : <span className="profile-fallback" aria-hidden="true">{user.displayName.slice(0, 1)}</span>}
        </button>
        <div><h2>{user.displayName}</h2><p className="muted">用户名 {user.username}<br />邮箱 {user.email}</p></div>
      </div>
      <button type="button" className="button profile-edit" aria-expanded={editing} onClick={() => setEditing(value => !value)}>{editing ? "完成" : "编辑信息"}</button>
    </section>
    {editing && <NicknameForm user={user} onSaved={replaceUser} />}
    {editing && <PasswordForm />}
    {avatarOpen && <AvatarDialog onSaved={replaceUser} close={() => setAvatarOpen(false)} />}
    <section className="editor-card" aria-labelledby="records-heading">
      <div className="section-heading"><h2 id="records-heading">最近的作品</h2><Link href="/history">查看全部记录</Link></div>
      {records.error ? <Feedback error={records.error} retry={records.retry} /> : !records.data ? <p role="status">正在读取个人记录…</p> : records.data.items.length === 0 ? <p className="muted">还没有作品。<Link href="/templates">去选择模板</Link></p> : <div className="task-grid">{records.data.items.map(task => <TaskCard key={task.id} task={task} />)}</div>}
    </section>
    <DeleteAccount username={user.username} />
  </div>;
}
function NicknameForm({ user, onSaved }: { user: User; onSaved: (user: User) => void }) {
  const [name, setName] = useState(user.displayName);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [pending, setPending] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const displayName = name.trim();
    if (!displayName || displayName.length > 40) { setError("昵称需要 1～40 个字。"); setSaved(false); return; }
    setPending(true); setError(""); setSaved(false);
    try {
      const data = await api<{ user: User }>("/api/account", { method: "PATCH", body: JSON.stringify({ displayName }) });
      onSaved(data.user); setSaved(true);
    } catch (cause) { setError((cause as Error).message); }
    finally { setPending(false); }
  }
  return <form className="editor-card editor-fields" onSubmit={submit}><h2>昵称</h2><label>显示名称<input value={name} maxLength={40} autoComplete="nickname" onChange={event => setName(event.target.value)} /></label>{error && <p className="feedback" role="alert">{error}</p>}{saved && <p role="status">昵称已更新。</p>}<button className="button" type="submit" disabled={pending}>{pending ? "正在保存…" : "保存昵称"}</button></form>;
}
function AvatarDialog({ onSaved, close }: { onSaved: (user: User) => void; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  useEffect(() => { const element = dialog.current!; element.showModal(); return () => element.close(); }, []);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!file) { setError("请选择 JPEG 或 PNG 头像。"); return; }
    if (file.size > 2 * 1024 * 1024) { setError("头像不能超过 2 MB。"); return; }
    setPending(true); setError("");
    const body = new FormData(); body.set("file", file);
    try {
      const data = await api<{ user: User }>("/api/account/avatar", { method: "POST", body });
      onSaved({ ...data.user, avatarUrl: data.user.avatarUrl ? `${data.user.avatarUrl}?v=${Date.now()}` : null });
      close();
    } catch (cause) { setError((cause as Error).message); setPending(false); }
  }
  return <dialog ref={dialog} className="account-dialog" aria-label="修改头像" onCancel={close}>
    <form className="editor-fields" onSubmit={submit}>
      <h2>修改头像</h2>
      <label>选择图片<input type="file" accept="image/jpeg,image/png" onChange={event => setFile(event.target.files?.[0] ?? null)} /></label>
      <p className="muted">支持 JPEG、PNG，最大 2 MB。只有你登录后能看到这张头像。</p>
      {error && <p className="feedback" role="alert">{error}</p>}
      <div className="editor-actions"><button className="button" type="submit" disabled={pending}>{pending ? "正在上传…" : "更新头像"}</button><button className="text-button" type="button" onClick={close}>取消</button></div>
    </form>
  </dialog>;
}
function PasswordForm() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [pending, setPending] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (newPassword.length < 8 || newPassword.length > 64) { setError("新密码需为 8～64 位。"); setSaved(false); return; }
    if (newPassword !== confirm) { setError("两次输入的新密码不一致。"); setSaved(false); return; }
    setPending(true); setError(""); setSaved(false);
    try {
      await api("/api/account/password", { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) });
      setCurrentPassword(""); setNewPassword(""); setConfirm(""); setSaved(true);
    } catch (cause) { setError((cause as Error).message); }
    finally { setPending(false); }
  }
  return <form className="editor-card editor-fields" onSubmit={submit}><h2>重置密码</h2><label>当前密码<input type="password" autoComplete="current-password" value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} required /></label><label>新密码<input type="password" autoComplete="new-password" minLength={8} maxLength={64} value={newPassword} onChange={event => setNewPassword(event.target.value)} required /></label><label>确认新密码<input type="password" autoComplete="new-password" minLength={8} maxLength={64} value={confirm} onChange={event => setConfirm(event.target.value)} required /></label>{error && <p className="feedback" role="alert">{error}</p>}{saved && <p role="status">密码已更新。下次登录请使用新密码。</p>}<button className="button" type="submit" disabled={pending}>{pending ? "正在更新…" : "更新密码"}</button></form>;
}
function DeleteAccount({ username }: { username: string }) {
  const router = useRouter();
  const { forget } = useSession();
  const [confirm, setConfirm] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (confirm !== username) { setError(`请输入用户名 ${username} 以确认删除。`); return; }
    if (!password || pending) return;
    setPending(true); setError("");
    try {
      await api("/api/account/delete", { method: "POST", body: JSON.stringify({ password }) });
      forget(); router.replace("/login");
    } catch (cause) { setError((cause as Error).message); setPending(false); }
  }
  return <form className="editor-card editor-fields danger-zone" onSubmit={submit}><h2>删除账户</h2><p className="muted">删除后无法恢复。你的素材、制作记录、结果图片和自建模板都会一并删除。公共模板仍会保留。</p><label>输入用户名 {username} 确认<input autoComplete="off" value={confirm} onChange={event => setConfirm(event.target.value)} /></label><label>当前密码<input type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required /></label>{error && <p className="feedback" role="alert">{error}</p>}<button className="button danger-button" type="submit" disabled={pending || confirm !== username}>{pending ? "正在删除…" : "删除账户"}</button></form>;
}
