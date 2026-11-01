import { api, terminal, type Page, type Task } from "./api";

type Snapshot = { tasks: Record<string, Task>; errors: Record<string, string>; syncError: string | null; ready: boolean };
type Transport = { task: (id: string, signal: AbortSignal) => Promise<Task>; active: (page: number, signal: AbortSignal) => Promise<Page<Task>> };
const transport: Transport = {
  task: (id, signal) => api(`/api/tasks/${encodeURIComponent(id)}`, { signal }),
  active: (page, signal) => api(`/api/tasks/active?page=${page}&pageSize=50`, { signal }),
};
export class TaskTracker {
  private state: Snapshot = { tasks: {}, errors: {}, syncError: null, ready: false };
  private listeners = new Set<() => void>();
  private timers = new Map<string, ReturnType<typeof setTimeout>>();
  private requests = new Map<string, AbortController>();
  private bootstrap?: AbortController;
  private stopped = false;
  constructor(private io = transport, private interval = 1500) {}
  getSnapshot = () => this.state;
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  private update(state: Snapshot) { if (this.stopped) return; this.state = state; this.listeners.forEach(fn => fn()); }
  merge = (task: Task) => {
    if (this.stopped) return;
    const previous = this.state.tasks[task.id];
    if (previous && (previous.version > task.version || (terminal(previous) && !terminal(task)))) return;
    const errors = { ...this.state.errors }; if (terminal(task)) delete errors[task.id];
    this.update({ ...this.state, tasks: { ...this.state.tasks, [task.id]: task }, errors });
    if (terminal(task)) this.stopTask(task.id);
    else if (!this.timers.has(task.id) && !this.requests.has(task.id) && !this.state.errors[task.id]) this.schedule(task.id);
  };
  private schedule(id: string) { if (!this.stopped) this.timers.set(id, setTimeout(() => { this.timers.delete(id); void this.query(id); }, this.interval)); }
  private stopTask(id: string) { clearTimeout(this.timers.get(id)); this.timers.delete(id); this.requests.get(id)?.abort(); this.requests.delete(id); }
  query = async (id: string) => {
    if (this.stopped || this.requests.has(id)) return;
    clearTimeout(this.timers.get(id)); this.timers.delete(id);
    const controller = new AbortController(); this.requests.set(id, controller);
    const errors = { ...this.state.errors }; delete errors[id]; this.update({ ...this.state, errors });
    try {
      const task = await this.io.task(id, controller.signal);
      if (!controller.signal.aborted && !this.stopped) this.merge(task);
    } catch (error) {
      if (!controller.signal.aborted) this.update({ ...this.state, errors: { ...this.state.errors, [id]: error instanceof Error ? error.message : "查询失败" } });
    } finally {
      if (this.requests.get(id) === controller) this.requests.delete(id);
      const task = this.state.tasks[id];
      if (!this.stopped && !controller.signal.aborted && task && !terminal(task) && !this.state.errors[id]) this.schedule(id);
    }
  };
  start = async () => {
    this.stopped = false;
    this.bootstrap?.abort(); const controller = new AbortController(); this.bootstrap = controller;
    this.update({ ...this.state, syncError: null });
    try {
      for (let page = 1; ; page++) {
        const data = await this.io.active(page, controller.signal);
        if (controller.signal.aborted || this.stopped) return;
        data.items.forEach(this.merge);
        if (page * data.pageSize >= data.total || data.items.length === 0) break;
      }
      this.update({ ...this.state, ready: true });
    } catch (error) {
      if (!controller.signal.aborted) this.update({ ...this.state, syncError: error instanceof Error ? error.message : "活动任务加载失败", ready: true });
    }
  };
  dispose = () => {
    this.stopped = true; this.bootstrap?.abort(); this.timers.forEach(clearTimeout); this.timers.clear();
    this.requests.forEach(controller => controller.abort()); this.requests.clear(); this.listeners.clear();
  };
}
