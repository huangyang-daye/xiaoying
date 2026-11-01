"use client";
import { createContext, useContext, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { api, ApiError, type User } from "@/lib/api";
import { TaskTracker } from "@/lib/task-tracker";

type Session = { user: User | null; loading: boolean; error: string | null; epoch: number };
const SessionContext = createContext<{ session: Session; select: (key: string) => Promise<void>; reload: () => void } | null>(null);
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
    const expire = () => { operation.current++; setSession(s => ({ user: null, loading: false, error: "会话已失效，请重新选择身份。", epoch: s.epoch + 1 })); };
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
      if (operation.current === id) {
        setSession(s => ({ ...s, user: data.user, loading: false }));
        try { localStorage.setItem("studio:identity", `${Date.now()}`); } catch { /* Cookies remain the identity source. */ }
      }
    } catch (error) { if (operation.current === id) setSession(s => ({ ...s, loading: false, error: (error as Error).message })); }
  };
  return <SessionContext value={{ session, select, reload: () => setReloadKey(k => k + 1) }}><TaskRuntime key={`${session.epoch}:${session.user?.id ?? "anonymous"}`} enabled={!!session.user}>{children}</TaskRuntime></SessionContext>;
}
export function IdentityControl() {
  const { session, select, reload } = useSession();
  return <div className="identity-control"><label htmlFor="identity">演示身份</label><select id="identity" value={session.user?.id ?? ""} disabled={session.loading} onChange={event => void select(event.target.value)}><option value="" disabled>{session.loading ? "正在连接…" : "请选择身份"}</option><option value="demo-a">演示用户 A</option><option value="demo-b">演示用户 B</option></select>{session.error && <button className="text-button" onClick={reload}>重新连接</button>}</div>;
}
export function RequireSession({ children }: { children: React.ReactNode }) {
  const { session } = useSession();
  if (session.loading) return <p role="status">正在确认身份…</p>;
  if (!session.user) return <div className="notice"><h2>选择身份，开始创作</h2><p>请在顶部选择演示用户 A 或 B。每个身份拥有各自的素材和作品记录。</p>{session.error && <p role="alert">{session.error}</p>}</div>;
  return children;
}
