import type { AuthSession, Message } from "../types/api";

function storageKey(session: AuthSession): string {
  return `private-chat-messages:${session.pairedToken.slice(-12)}`;
}

export function loadLocalMessages(session: AuthSession): Message[] {
  const raw = window.localStorage.getItem(storageKey(session));
  if (!raw) return [];
  try {
    const messages = JSON.parse(raw) as Message[];
    return Array.isArray(messages) ? messages.sort(compareMessages) : [];
  } catch {
    return [];
  }
}

export function saveLocalMessages(session: AuthSession, messages: Message[]): void {
  window.localStorage.setItem(storageKey(session), JSON.stringify(messages.sort(compareMessages)));
}

export function mergeMessages(local: Message[], remote: Message[]): Message[] {
  const merged = new Map(local.map((message) => [message.id, message]));
  for (const message of remote) {
    merged.set(message.id, message);
  }
  return [...merged.values()].sort(compareMessages);
}

function compareMessages(left: Message, right: Message): number {
  return left.timestamp.localeCompare(right.timestamp) || left.id.localeCompare(right.id);
}
