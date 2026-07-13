import type { LoginResponse } from "../types/api";

export const backendBaseUrl = import.meta.env.VITE_BACKEND_URL ??
  (import.meta.env.PROD ? window.location.origin : "http://localhost:8787");

async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  const response = await fetch(`${backendBaseUrl}${path}`, { ...options, headers });
  if (!response.ok) {
    const fallback = response.status === 401 ? "The pairing code is not active." : "The relay is unavailable.";
    const body = await response.json().catch(() => ({ error: fallback }));
    throw new Error(typeof body.error === "string" ? body.error : fallback);
  }
  return response.json() as Promise<T>;
}

export function login(pairingCode: string): Promise<LoginResponse> {
  return requestJson<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ pairingCode })
  });
}
