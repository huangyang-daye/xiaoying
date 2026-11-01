"use client";
import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
export function useRemote<T>(url: string, refreshKey = "") {
  const [attempt, setAttempt] = useState(0);
  const token = `${refreshKey}\n${url}`;
  const key = `${token}:${attempt}`;
  const [state, setState] = useState<{ key: string; token: string; data?: T; error?: ApiError }>({ key: "", token: "" });
  useEffect(() => {
    const controller = new AbortController();
    void api<T>(url, { signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) setState({ key, token, data });
    }).catch(error => { if (!controller.signal.aborted) setState(previous => ({ key, token, data: previous.token === token ? previous.data : undefined, error })); });
    return () => controller.abort();
  }, [url, key, token]);
  return { data: state.token === token ? state.data : undefined, error: state.key === key ? state.error : undefined, loading: state.key !== key, retry: () => setAttempt(n => n + 1) };
}
