"use client";
import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
export function useRemote<T>(url: string) {
  const [attempt, setAttempt] = useState(0);
  const key = `${url}:${attempt}`;
  const [state, setState] = useState<{ key: string; url: string; data?: T; error?: ApiError }>({ key: "", url: "" });
  useEffect(() => {
    const controller = new AbortController();
    void api<T>(url, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setState({ key, url, data });
    }).catch(error => { if (!controller.signal.aborted) setState(previous => ({ key, url, data: previous.url === url ? previous.data : undefined, error })); });
    return () => controller.abort();
  }, [url, key]);
  return { data: state.url === url ? state.data : undefined, error: state.key === key ? state.error : undefined, loading: state.key !== key, retry: () => setAttempt(n => n + 1) };
}
