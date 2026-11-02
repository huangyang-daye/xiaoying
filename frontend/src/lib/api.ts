export type User = { id: string; username: string; email: string; displayName: string; avatarUrl: string | null };
export type Category = { id: string; name: string };
export type Page<T> = { items: T[]; total: number; page: number; pageSize: number; nextCursor?: string | null };
export type Template = { id: string; categoryId: string; title: string; description: string; coverUrl: string; requiredImageCount: number; acceptedMimeTypes: string[]; maxFileSizeBytes: number; storedCoverUrl: string | null; sampleResultUrl: string | null; ownerId: string | null };
export type Asset = { assetId: string; previewUrl: string; mimeType: string; sizeBytes: number };
export type Status = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type Stage = "NOT_STARTED" | "MATERIAL_CHECK" | "MOCK_GENERATION" | "RESULT_ASSEMBLY" | "COMPLETED";
export type Task = { id: string; templateId: string; templateTitle: string; status: Status; currentStage: Stage; failureStage: Stage | null; errorCode: string | null; errorMessage: string | null; result: { id: string; url: string; mimeType: string; sizeBytes: number } | null; version: number; createdAt: string; startedAt: string | null; finishedAt: string | null; attemptCount?: number; maxAttempts?: number; nextAttemptAt?: string | null; lastErrorCode?: string | null; lastErrorMessage?: string | null; attempts?: { attemptNo: number; stage: Stage; outcome: string; errorMessage: string | null }[]; steps: { stage: Stage; status: string; sequenceNo: number; attemptCount?: number }[] };
export const statusText: Record<Status, string> = { QUEUED: "等待处理", RUNNING: "处理中", SUCCEEDED: "制作成功", FAILED: "制作失败" };
export const stageText: Record<Stage, string> = { NOT_STARTED: "等待开始", MATERIAL_CHECK: "素材检查", MOCK_GENERATION: "模拟生成", RESULT_ASSEMBLY: "结果整理", COMPLETED: "已完成" };
export const terminal = (task: Task) => task.status === "SUCCEEDED" || task.status === "FAILED";
export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message); }
}
export async function api<T>(path: string, options: RequestInit = {}, timeout = 10000): Promise<T> {
  const signal = AbortSignal.any([AbortSignal.timeout(timeout), ...(options.signal ? [options.signal] : [])]);
  try {
    const response = await fetch(path, { ...options, credentials: "same-origin", cache: "no-store", signal,
      headers: { ...(typeof options.body === "string" ? { "Content-Type": "application/json" } : {}), ...options.headers } });
    signal.throwIfAborted();
    if (!response.ok) {
      const error = await response.json().catch(() => null);
      signal.throwIfAborted();
      const anonymousAuth = path === "/api/session" || path === "/api/session/login" || path === "/api/accounts";
      if (response.status === 401 && !anonymousAuth && typeof window !== "undefined") window.dispatchEvent(new Event("studio:session-expired"));
      throw new ApiError(error?.message ?? `请求失败（${response.status}）`, response.status);
    }
    return response.status === 204 ? undefined as T : await response.json() as T;
  } catch (error) {
    if (options.signal?.aborted) throw error;
    if (error instanceof ApiError) throw error;
    throw new ApiError("网络请求失败或超时，请检查连接后重试。", 0);
  }
}
