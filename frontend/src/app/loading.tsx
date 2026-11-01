export default function Loading() {
  return (
    <div className="page-skeleton" role="status" aria-label="页面加载中">
      <span className="skeleton skeleton-kicker" />
      <span className="skeleton skeleton-title" />
      <span className="skeleton skeleton-description" />
      <span className="skeleton skeleton-panel" />
      <span className="sr-only">页面加载中，请稍候。</span>
    </div>
  );
}
