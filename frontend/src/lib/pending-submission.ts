export type SubmissionIntent = { key: string; body: string };
/** Storage is partitioned by account and template; memory remains usable if storage is blocked. */
export class PendingSubmissionStore {
  private loaded = false;
  private pending: SubmissionIntent | null = null;
  constructor(private name: string, private storage: () => Pick<Storage, "getItem" | "setItem" | "removeItem"> = () => window.sessionStorage,
    private key: () => string = () => Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, "0")).join("")) {}
  get(): SubmissionIntent | null {
    if (!this.loaded) {
      this.loaded = true;
      try {
        const value = JSON.parse(this.storage().getItem(this.name) ?? "null");
        if (value && typeof value.body === "string" && typeof value.key === "string" && /^[a-z0-9][a-z0-9._:-]{0,127}$/.test(value.key)) this.pending = value;
      } catch { /* Keep the in-memory fallback. */ }
    }
    return this.pending;
  }
  prepare(body: string): SubmissionIntent {
    const pending = this.get();
    if (pending && pending.body !== body) throw new Error("上次提交尚未确认，请先恢复提交，或核对历史后清除恢复记录。");
    this.pending = pending ?? { body, key: this.key() };
    try { this.storage().setItem(this.name, JSON.stringify(this.pending)); } catch { /* The current page can still retry safely. */ }
    return this.pending;
  }
  clear(key?: string) {
    if (key && this.get()?.key !== key) return;
    this.pending = null; this.loaded = true;
    try { this.storage().removeItem(this.name); } catch { /* Cleared in memory. */ }
  }
}
