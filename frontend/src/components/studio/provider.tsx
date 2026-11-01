"use client";
/* eslint-disable @next/next/no-img-element -- The avatar is a session-protected file. */
import Link from "next/link";
import { createContext, useContext, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { api, ApiError, type User } from "@/lib/api";
import { TaskTracker } from "@/lib/task-tracker";

type Session = { user: User | null; loading: boolean; error: string | null; epoch: number };
type SessionActions = {
  session: Session;
  select: (key: string) => Promise<void>;
  login: (login: string, password: string) => Promise<void>;
  register: (body: { email: string; username: string; password: string; displayName?: string }) => Promise<void>;
  logout: () => Promise<void>;
  replaceUser: (user: User) => void;
  forget: () => void;
  reload: () => void;
};
const SessionContext = createContext<SessionActions | null>(null);
const TasksContext = createContext<TaskTracker | null>(null);
export const useSession = () => useContext(SessionContext)!;
export function useTasks() {
  const tracker = useContext(TasksContext)!;
  return { ...useSyncExternalStore(tracker.subscribe, tracker.getSnapshot, tracker.getSnapshot), tracker };
}
function TaskRuntime({ children, enabled }: { children: React.ReactNode; enabled: boolean }) {
  const [tracker] = useState(() => new TaskTracker());
  useEffect(() => {
    if (enabled) void tracker.start();
    return tracker.dispose;
  }, [enabled, tracker]);
  return <TasksContext value={tracker}>{children}</TasksContext>;
}
function rememberIdentity() {
  try { localStorage.setItem("studio:identity", `${Date.now()}`); } catch { /* Cookies remain the identity source. */ }
}
export function StudioProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session>({ user: null, loading: true, error: null, epoch: 0 });
  const [reloadKey, setReloadKey] = useState(0);
  const operation = useRef(0);
  useEffect(() => {
    const id = ++operation.current; const controller = new AbortController();
    void api<{ user: User }>("/api/session", { signal: controller.signal }).then(data => {
      if (operation.current === id && !controller.signal.aborted) setSession(s => ({ ...s, user: data.user, loading: false, error: null }));
    }).catch(error => {
      if (operation.current === id && !controller.signal.aborted) setSession(s => ({ ...s, user: null, loading: false, error: error instanceof ApiError && error.status === 401 ? null : error.message }));
    });
    return () => controller.abort();
  }, [reloadKey]);
  useEffect(() => {
    const expire = () => { operation.current++; setSession(s => ({ user: null, loading: false, error: "会话已失效，请重新登录。", epoch: s.epoch + 1 })); };
    window.addEventListener("studio:session-expired", expire);
    const refresh = () => { operation.current++; setSession(s => ({ user: null, loading: true, error: null, epoch: s.epoch + 1 })); setReloadKey(k => k + 1); };
    window.addEventListener("studio:identity-changed", refresh);
    const otherTab = (event: StorageEvent) => { if (event.key === "studio:identity") refresh(); };
    window.addEventListener("storage", otherTab);
    return () => { window.removeEventListener("studio:session-expired", expire); window.removeEventListener("studio:identity-changed", refresh); window.removeEventListener("storage", otherTab); };
  }, []);
  const select = async (key: string) => {
    if (!key || session.loading) return;
    const id = ++operation.current;
    setSession(s => ({ user: null, loading: true, error: null, epoch: s.epoch + 1 }));
    try {
      const data = await api<{ user: User }>("/api/session/demo", { method: "POST", body: JSON.stringify({ demoKey: key }) });
      if (operation.current === id) { setSession(s => ({ ...s, user: data.user, loading: false })); rememberIdentity(); }
    } catch (error) { if (operation.current === id) setSession(s => ({ ...s, loading: false, error: (error as Error).message })); }
  };
  const authenticate = async (path: string, body: unknown) => {
    const id = ++operation.current;
    setSession(s => ({ ...s, loading: true, error: null }));
    try {
      const data = await api<{ user: User }>(path, { method: "POST", body: JSON.stringify(body) });
      if (operation.current === id) { setSession(s => ({ ...s, user: data.user, loading: false, error: null, epoch: s.epoch + 1 })); rememberIdentity(); }
    } catch (error) {
      if (operation.current === id) setSession(s => ({ ...s, loading: false }));
      throw error;
    }
  };
  const logout = async () => {
    const id = ++operation.current;
    try { await api("/api/session/logout", { method: "POST" }); } catch { /* The local session is cleared either way. */ }
    if (operation.current === id) { setSession(s => ({ user: null, loading: false, error: null, epoch: s.epoch + 1 })); rememberIdentity(); }
  };
  const forget = () => { operation.current++; setSession(s => ({ user: null, loading: false, error: null, epoch: s.epoch + 1 })); rememberIdentity(); };
  return <SessionContext value={{ session, select, login: (login, password) => authenticate("/api/session/login", { login, password }), register: body => authenticate("/api/accounts", body), logout, replaceUser: user => setSession(s => ({ ...s, user, loading: false, error: null })), forget, reload: () => setReloadKey(k => k + 1) }}><TaskRuntime key={`${session.epoch}:${session.user?.id ?? "anonymous"}`} enabled={!!session.user}>{children}</TaskRuntime></SessionContext>;
}
export function IdentityControl() {
  const { session, select, logout, reload } = useSession();
  const demoId = session.user?.id === "demo-a" || session.user?.id === "demo-b" ? session.user.id : "";
  return <div className="identity-control">
    {session.user ? <>
      <Link href="/account" className="account-chip" aria-label={`${session.user.displayName} 的个人设置`}>{session.user.avatarUrl ? <img src={session.user.avatarUrl} alt="" width={28} height={28} /> : <span className="avatar-fallback" aria-hidden="true">{session.user.displayName.slice(0, 1)}</span>}<span>{session.user.displayName}</span></Link>
      <button type="button" className="text-button" onClick={() => void logout()}>退出</button>
    </> : <>
      <Link href="/login">登录</Link>
      <Link href="/register">注册</Link>
    </>}
    <label htmlFor="identity">演示身份</label>
    <select id="identity" value={demoId} disabled={session.loading} onChange={event => void select(event.target.value)}>
      <option value="" disabled>{session.loading ? "正在连接…" : session.user && !demoId ? session.user.displayName : "请选择身份"}</option>
      <option value="demo-a">演示用户 A</option>
      <option value="demo-b">演示用户 B</option>
    </select>
    {session.error && <button className="text-button" onClick={reload}>重新连接</button>}
  </div>;
}
export function RequireSession({ children }: { children: React.ReactNode }) {
  const { session } = useSession();
  if (session.loading) return <p role="status">正在确认身份…</p>;
  if (!session.user) return <div className="notice"><h2>登录后开始创作</h2><p>创建账户时依次填写邮箱、用户名和密码，也可以在顶部选择演示用户 A 或 B。每个账户的素材和作品互相隔离。</p><p><Link href="/register">创建账户</Link> · <Link href="/login">登录</Link></p>{session.error && <p role="alert">{session.error}</p>}</div>;
  return children;
}
