import type { AuthSession, LoginResponse } from "../types/api";

const storageKey = "private-chat-auth";

export function loadSession(): AuthSession | null {
  const raw = window.localStorage.getItem(storageKey);
  if (!raw) {
    return null;
  }

  try {
    const session = JSON.parse(raw) as AuthSession;
    if (!session.endpointUrl || !session.pairedToken) {
      window.localStorage.removeItem(storageKey);
      return null;
    }
    return session;
  } catch {
    window.localStorage.removeItem(storageKey);
    return null;
  }
}

export function saveSession(response: LoginResponse): AuthSession {
  const session: AuthSession = {
    ...response,
    signedInAt: new Date().toISOString()
  };
  window.localStorage.setItem(storageKey, JSON.stringify(session));
  return session;
}

export function clearSession(): void {
  window.localStorage.removeItem(storageKey);
}
