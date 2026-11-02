import { describe, expect, it } from "vitest";
import { PendingSubmissionStore } from "./pending-submission";
const memory = () => {
  const data = new Map<string, string>();
  return { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); }, removeItem: (key: string) => { data.delete(key); } };
};
describe("submission intent persistence", () => {
  it("reuses the same key after an unknown response and page reload", () => {
    const storage = memory(); const first = new PendingSubmissionStore("a:template", () => storage, () => "intent-1");
    expect(first.prepare("payload").key).toBe("intent-1");
    const reloaded = new PendingSubmissionStore("a:template", () => storage, () => "intent-2");
    expect(reloaded.prepare("payload").key).toBe("intent-1");
    reloaded.clear("intent-1"); expect(reloaded.prepare("payload").key).toBe("intent-2");
  });
  it("does not silently replace an uncertain submission with changed parameters", () => {
    const store = new PendingSubmissionStore("a", memory, () => "intent-1");
    store.prepare("first"); expect(() => store.prepare("changed")).toThrow("上次提交尚未确认");
    store.clear(); expect(store.prepare("changed").body).toBe("changed");
  });
  it("isolates identities and ignores acknowledgements for another intent", () => {
    const storage = memory(); const a = new PendingSubmissionStore("a", () => storage, () => "intent-a");
    const b = new PendingSubmissionStore("b", () => storage, () => "intent-b");
    a.prepare("a"); b.prepare("b"); a.clear("another-key");
    expect(a.get()?.key).toBe("intent-a"); b.clear(); expect(a.get()?.key).toBe("intent-a");
  });
  it("keeps the key in memory when browser storage is unavailable", () => {
    let sequence = 0; const store = new PendingSubmissionStore("a", () => { throw new Error("blocked"); }, () => `key-${++sequence}`);
    expect(store.prepare("a").key).toBe(store.prepare("a").key);
  });
  it("generates a cryptographically random key without requiring secure-context randomUUID", () => {
    const store = new PendingSubmissionStore("http-page", memory);
    const first = store.prepare("first").key;
    expect(first).toMatch(/^[0-9a-f]{32}$/);
    store.clear(); expect(store.prepare("second").key).not.toBe(first);
  });

});
