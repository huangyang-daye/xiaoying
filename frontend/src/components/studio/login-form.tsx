"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useSession } from "./provider";
import { PageHeading } from "@/components/ui/page-heading";

export function LoginForm() {
  const router = useRouter();
  const { login } = useSession();
  const [account, setAccount] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!account.trim() || !password || pending) { setError("请填写用户名或邮箱，以及密码。"); return; }
    setPending(true); setError("");
    try { await login(account.trim(), password); router.push("/account"); }
    catch (cause) { setError((cause as Error).message); }
    finally { setPending(false); }
  }
  return <>
    <PageHeading eyebrow="WELCOME BACK" title="登录你的映作账户。" description="使用用户名或邮箱和密码进入。登录后可以查看自己的作品，并在个人设置里修改资料。" />
    <form className="editor-card editor-fields account-form" onSubmit={submit}>
      <label>用户名或邮箱<input autoComplete="username" required value={account} onChange={event => setAccount(event.target.value)} /></label>
      <label>密码<input type="password" autoComplete="current-password" required value={password} onChange={event => setPassword(event.target.value)} /></label>
      {error && <p className="feedback" role="alert">{error}</p>}
      <div className="editor-actions"><button className="button" type="submit" disabled={pending}>{pending ? "正在登录…" : "登录"}</button></div>
      <p className="muted">还没有账户？<Link href="/register">按步骤创建</Link>。演示账户用户名是 demo-a 或 demo-b，初始密码是 demo-password。</p>
    </form>
  </>;
}
