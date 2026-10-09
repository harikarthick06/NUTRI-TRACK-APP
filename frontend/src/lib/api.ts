import type { ApiErrorBody, AuthResponse } from "./types";

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, "") ?? "";

/** The access token lives only in memory; the refresh token is an HttpOnly cookie the browser sends itself. */
let accessToken: string | null = null;
let onSessionExpired: (() => void) | null = null;
let refreshing: Promise<AuthResponse | null> | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler;
}

export class ApiError extends Error {
  status: number;
  code?: string;
  fieldErrors: Record<string, string>;

  constructor(status: number, body: Partial<ApiErrorBody> | null) {
    super(body?.message ?? (status === 0 ? "Can't reach the server. Check your connection." : "Something went wrong"));
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = {};
    for (const fe of body?.fieldErrors ?? []) this.fieldErrors[fe.field] = fe.message;
  }
}

/** Uses the refresh cookie to get a new access token. Concurrent callers share one request. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshing) {
    refreshing = fetch(`${BASE}/api/auth/refresh`, { method: "POST", credentials: "include" })
      .then(async (res) => {
        if (!res.ok) return null;
        const body = (await res.json()) as AuthResponse;
        accessToken = body.accessToken;
        return body;
      })
      .catch(() => null)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  query?: Query;
  /** Return the raw Response (for file downloads). */
  raw?: boolean;
  /** Skip the refresh-and-retry on 401 (used by auth calls). */
  noRetry?: boolean;
}

export function buildUrl(path: string, query?: Query): string {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v !== undefined && v !== null && v !== "") params.set(k, String(v));
  }
  const qs = params.toString();
  return `${BASE}${path}${qs ? `?${qs}` : ""}`;
}

async function send(path: string, opts: RequestOptions): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  try {
    return await fetch(buildUrl(path, opts.query), {
      method: opts.method ?? "GET",
      headers,
      credentials: "include",
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    });
  } catch {
    throw new ApiError(0, null);
  }
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  let res = await send(path, opts);
  if (res.status === 401 && !opts.noRetry) {
    const refreshed = await refreshSession();
    if (refreshed) {
      res = await send(path, opts);
    } else {
      accessToken = null;
      onSessionExpired?.();
    }
  }
  if (!res.ok) {
    let body: Partial<ApiErrorBody> | null = null;
    try {
      body = (await res.json()) as ApiErrorBody;
    } catch {
      body = null;
    }
    throw new ApiError(res.status, body);
  }
  if (opts.raw) return res as unknown as T;
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Downloads an authenticated file (CSV export) through a temporary object URL. */
export async function download(path: string, query: Query, fallbackName: string): Promise<void> {
  const res = await api<Response>(path, { query, raw: true });
  const blob = await res.blob();
  const disposition = res.headers.get("Content-Disposition") ?? "";
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = match?.[1] ?? fallbackName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return "Something went wrong";
}
