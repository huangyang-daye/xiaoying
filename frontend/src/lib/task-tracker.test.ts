import { afterEach, describe, expect, it, vi } from "vitest";
import { TaskTracker } from "./task-tracker";
import type { Task } from "./api";
const task = (id: string, status: Task["status"] = "RUNNING", version = 1) => ({ id, status, version, currentStage: "MOCK_GENERATION" } as Task);
const page = (items: Task[]) => ({ items, total: items.length, page: 1, pageSize: 50 });
afterEach(() => vi.useRealTimers());
describe("per-session task tracking", () => {
  it("keeps tasks independent, stops terminal polling, and refuses terminal regression", async () => {
    vi.useFakeTimers();
    const fetch = vi.fn(async (id: string) => task(id, id === "a" ? "SUCCEEDED" : "FAILED", 2));
    const tracker = new TaskTracker({ task: fetch, active: async () => page([task("a"), task("b")]) }, 100);
    await tracker.start(); await vi.advanceTimersByTimeAsync(100);
    expect(tracker.getSnapshot().tasks.a.status).toBe("SUCCEEDED");
    expect(tracker.getSnapshot().tasks.b.status).toBe("FAILED");
    tracker.merge(task("a", "RUNNING", 3));
    expect(tracker.getSnapshot().tasks.a.status).toBe("SUCCEEDED");
    await vi.advanceTimersByTimeAsync(1000); expect(fetch).toHaveBeenCalledTimes(2); tracker.dispose();
  });
  it("retains the business state on query errors and resumes only on explicit recovery", async () => {
    vi.useFakeTimers(); const fetch = vi.fn().mockRejectedValueOnce(new Error("network down")).mockResolvedValueOnce(task("a", "SUCCEEDED", 2));
    const tracker = new TaskTracker({ task: fetch, active: async () => page([task("a")]) }, 100);
    await tracker.start(); await vi.advanceTimersByTimeAsync(100);
    expect(tracker.getSnapshot().tasks.a.status).toBe("RUNNING"); expect(tracker.getSnapshot().errors.a).toBe("network down");
    await vi.advanceTimersByTimeAsync(1000); expect(fetch).toHaveBeenCalledTimes(1);
    await tracker.query("a"); expect(tracker.getSnapshot().tasks.a.status).toBe("SUCCEEDED"); expect(tracker.getSnapshot().errors.a).toBeUndefined(); tracker.dispose();
  });
  it("ignores late responses after switching identity and cancels timers", async () => {
    vi.useFakeTimers(); let finish!: (task: Task) => void;
    const tracker = new TaskTracker({ task: () => new Promise(resolve => { finish = resolve; }), active: async () => page([task("a")]) }, 100);
    await tracker.start(); await vi.advanceTimersByTimeAsync(100); const before = tracker.getSnapshot(); tracker.dispose(); finish(task("a", "SUCCEEDED", 2));
    await Promise.resolve(); expect(tracker.getSnapshot()).toBe(before); expect(vi.getTimerCount()).toBe(0);
  });
  it("restores all pages and prevents overlapping queries for the same task", async () => {
    const active = vi.fn(async (n: number) => ({ items: [task(String(n))], total: 2, page: n, pageSize: 1 }));
    let finish!: (task: Task) => void; const fetch = vi.fn(() => new Promise<Task>(resolve => { finish = resolve; }));
    const tracker = new TaskTracker({ task: fetch, active }); await tracker.start(); expect(active).toHaveBeenCalledTimes(2);
    const pending = tracker.query("1"); await tracker.query("1"); expect(fetch).toHaveBeenCalledTimes(1); finish(task("1", "SUCCEEDED", 2)); await pending; tracker.dispose();
  });
});
