"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { IdentityControl, useSession, useTasks } from "@/components/studio/provider";
import { terminal } from "@/lib/api";

const navigation = [
  { href: "/templates", label: "探索模板" },
  { href: "/history", label: "我的作品" },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { session } = useSession();
  const { tasks, errors, syncError, tracker } = useTasks();
  const active = Object.values(tasks).filter(task => !terminal(task)).length;

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">跳转到主要内容</a>
      <header className="site-header">
        <div className="header-inner">
          <Link href="/templates" className="brand" aria-label="映作，返回模板首页">
            <span className="brand-symbol" aria-hidden="true">映</span>
            <span className="brand-name">映作<span className="brand-caption">STUDIO</span></span>
          </Link>
          <nav className="main-nav" aria-label="主导航">
            {navigation.map(({ href, label }) => {
              const active = pathname === href || pathname.startsWith(`${href}/`) ||
                (href === "/history" && pathname.startsWith("/tasks/"));
              return <Link key={href} href={href} aria-current={active ? "page" : undefined}>{label}</Link>;
            })}
          </nav>
          <IdentityControl />
        </div>
      </header>
      <main id="main-content" className="main-content" tabIndex={-1}>
        {session.user && (active > 0 || Object.keys(errors).length > 0) && <div className="activity-bar"><Link href="/history">{active} 个任务等待或制作中 · 查看我的作品</Link>{Object.keys(errors).length > 0 && <span>部分任务查询暂停，可在详情恢复</span>}</div>}
        {session.error && <p className="feedback" role="alert">{session.error}</p>}
        {syncError && <p className="feedback" role="alert">未能恢复活动任务：{syncError} <button className="text-button" onClick={() => void tracker.start()}>恢复查询</button></p>}
        {children}
      </main>
      <footer className="site-footer"><span>映作 STUDIO</span><span>从一张图片，开始新的创作。</span></footer>
    </div>
  );
}
