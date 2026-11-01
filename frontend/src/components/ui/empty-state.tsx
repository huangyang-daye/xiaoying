import Link from "next/link";

type EmptyStateProps = {
  title: string;
  description: string;
  action?: { href: string; label: string };
};

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <section className="empty-state">
      <div className="empty-art" aria-hidden="true">
        <svg viewBox="0 0 64 64" fill="none"><rect x="13" y="12" width="38" height="40" rx="8" /><circle cx="38" cy="25" r="4" /><path d="m14 43 12-13 12 13 6-6 7 7" /><path d="M7 20v-5a9 9 0 0 1 9-9h5M43 58h5a9 9 0 0 0 9-9v-5" /></svg>
      </div>
      <h2>{title}</h2>
      <p>{description}</p>
      {action && <Link className="button button-secondary" href={action.href}>{action.label}<span aria-hidden="true">↗</span></Link>}
    </section>
  );
}
