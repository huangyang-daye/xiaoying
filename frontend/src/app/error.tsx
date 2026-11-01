"use client";

export default function ErrorPage({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <section className="empty-state error-state" role="alert">
      <p className="eyebrow">SOMETHING WENT WRONG</p>
      <h1>页面暂时无法加载</h1>
      <p>请稍后重试，继续你的创作旅程。</p>
      <button type="button" className="button button-secondary" onClick={reset}>重新加载</button>
    </section>
  );
}
