"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useSession } from "./provider";
import { PageHeading } from "@/components/ui/page-heading";

const steps = ["邮箱", "用户名", "密码"];
const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const usernamePattern = /^[a-z0-9][a-z0-9_-]{2,31}$/;

export function RegisterForm() {
  const router = useRouter();
  const { session, register } = useSession();
  const [step, setStep] = useState(0);
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);

  function next() {
    const problem = step === 0 ? emailProblem(email) : usernameProblem(username);
    setError(problem);
    if (!problem) setStep(value => value + 1);
  }
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const problem = passwordProblem(password, confirm, email, username);
    setError(problem);
    if (problem || pending) return;
    setPending(true);
    try {
      await register({ email: email.trim(), username: username.trim(), password, ...(displayName.trim() ? { displayName: displayName.trim() } : {}) });
      router.push("/account");
    } catch (cause) { setError((cause as Error).message); }
    finally { setPending(false); }
  }
  return <>
    <PageHeading eyebrow="CREATE YOUR STUDIO" title="创建你的映作账户。" description="按顺序填写邮箱、用户名和密码。创建后只看得到自己的素材、模板和作品。" />
    {session.user && <p className="muted">当前已登录 {session.user.displayName}。创建新账户后会切换到新身份。</p>}
    <form className="editor-card editor-fields account-form" onSubmit={submit}>
      <ol className="wizard-steps" aria-label="注册步骤">{steps.map((label, index) => <li key={label} aria-current={step === index ? "step" : undefined}>{index + 1}. {label}</li>)}</ol>
      {step === 0 && <label>邮箱<input type="email" inputMode="email" autoComplete="email" required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} /><span className="muted">用于登录和识别账户，会保存为小写。</span></label>}
      {step === 1 && <label>用户名<input autoComplete="username" required spellCheck={false} maxLength={32} value={username} onChange={event => setUsername(event.target.value)} /><span className="muted">3～32 位字母、数字、下划线或连字符，用于登录。昵称可以另外设置。</span></label>}
      {step === 2 && <>
        <label>密码<input type="password" autoComplete="new-password" required minLength={8} maxLength={64} value={password} onChange={event => setPassword(event.target.value)} /><span className="muted">8～64 位，不要与用户名或邮箱相同。</span></label>
        <label>确认密码<input type="password" autoComplete="new-password" required minLength={8} maxLength={64} value={confirm} onChange={event => setConfirm(event.target.value)} /></label>
        <label>昵称（可选）<input autoComplete="nickname" maxLength={40} value={displayName} onChange={event => setDisplayName(event.target.value)} placeholder="留空时与用户名相同" /></label>
      </>}
      {error && <p className="feedback" role="alert">{error}</p>}
      <div className="editor-actions">
        {step > 0 && <button type="button" className="text-button" disabled={pending} onClick={() => { setError(""); setStep(value => value - 1); }}>上一步</button>}
        {step < 2 ? <button type="button" className="button" onClick={next}>下一步</button> : <button className="button" type="submit" disabled={pending}>{pending ? "正在创建…" : "创建账户"}</button>}
      </div>
      <p className="muted">已有账户？<Link href="/login">去登录</Link>。演示用户 A、B 仍可在顶部直接选择，初始密码都是 demo-password。</p>
    </form>
  </>;
}
function emailProblem(value: string) {
  const email = value.trim();
  if (!email || email.length > 254 || !emailPattern.test(email)) return "请填写有效邮箱。";
  return "";
}
function usernameProblem(value: string) {
  const username = value.trim().toLowerCase();
  if (username === "demo-a" || username === "demo-b") return "该用户名已保留。";
  if (!usernamePattern.test(username)) return "用户名需为 3～32 位字母、数字、下划线或连字符，并以字母或数字开头。";
  return "";
}
function passwordProblem(password: string, confirm: string, email: string, username: string) {
  if (password.length < 8 || password.length > 64) return "密码需为 8～64 位。";
  if (password !== confirm) return "两次输入的密码不一致。";
  const normalized = username.trim().toLowerCase();
  if (password.toLowerCase() === normalized || password.toLowerCase() === email.trim().toLowerCase()) return "密码不能与用户名或邮箱相同。";
  return "";
}
