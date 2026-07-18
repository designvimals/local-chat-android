import type { AuthSession, Message } from "../types/api";

export function canonicalAttachments(message: Message) {
  const seen = new Set<string>();
  return [...(message.attachments ?? []), ...(message.attachment ? [message.attachment] : [])]
    .filter((attachment) => {
      if (seen.has(attachment.id)) return false;
      seen.add(attachment.id);
      return true;
    });
}

function storageKey(session: AuthSession): string {
  return `private-chat-messages:${session.pairedToken.slice(-12)}`;
}

export function loadLocalMessages(session: AuthSession): Message[] {
  const raw = window.localStorage.getItem(storageKey(session));
  if (!raw) return [];
  try {
    const messages = JSON.parse(raw) as Message[];
    return Array.isArray(messages) ? messages.map(normalizeMessage).sort(compareMessages) : [];
  } catch {
    return [];
  }
}

export function saveLocalMessages(session: AuthSession, messages: Message[]): void {
  window.localStorage.setItem(storageKey(session), JSON.stringify(messages.map(normalizeMessage).sort(compareMessages)));
}

export function messageSyncRevision(message: Message): string {
  const normalized = normalizeMessage(message);
  const canonical = [
    normalized.updatedAt ?? normalized.timestamp,
    normalized.status,
    normalized.deliveredAt ?? "",
    normalized.readAt ?? "",
    normalized.deletedAt ?? "",
    [...(normalized.deletedForDeviceIds ?? [])].sort().join(","),
    normalized.text,
    normalized.emphasisLevel ?? 0,
    normalized.replyToMessageId ?? "",
    normalized.editedAt ?? "",
    canonicalAttachments(normalized)
      .map((attachment) => `${attachment.id}:${attachment.name}:${attachment.mimeType}:${attachment.size}:${attachment.width ?? ""}x${attachment.height ?? ""};`)
      .join(""),
    [...(normalized.reactions ?? [])]
      .sort((left, right) => left.emoji < right.emoji ? -1 : left.emoji > right.emoji ? 1 : 0)
      .map((reaction) => `${reaction.emoji}:${[...reaction.reactorDeviceIds].sort().join(",")};`)
      .join("")
  ].join("|");
  return fnv1a(canonical, 0x811c9dc5) + fnv1a(canonical, 0x9e3779b9);
}

export function mergeMessages(local: Message[], remote: Message[]): Message[] {
  const merged = new Map(local.map((message) => {
    const normalized = normalizeMessage(message);
    return [normalized.id, normalized] as const;
  }));
  for (const rawMessage of remote) {
    const message = normalizeMessage(rawMessage);
    const current = merged.get(message.id);
    merged.set(message.id, current ? mergeMessage(current, message) : message);
  }
  return [...merged.values()].sort(compareMessages);
}

function mergeMessage(current: Message, candidate: Message): Message {
  const candidateUpdatedAt = candidate.updatedAt ?? candidate.timestamp;
  const currentUpdatedAt = current.updatedAt ?? current.timestamp;
  const latest = candidateUpdatedAt > currentUpdatedAt
    ? candidate
    : candidateUpdatedAt < currentUpdatedAt
      ? current
      : mutationKey(candidate) >= mutationKey(current) ? candidate : current;
  const deletedAt = maxTimestamp(current.deletedAt, candidate.deletedAt);
  return normalizeMessage({
    ...latest,
    status: statusRank(candidate.status) > statusRank(current.status) ? candidate.status : current.status,
    deliveredAt: maxTimestamp(current.deliveredAt, candidate.deliveredAt),
    readAt: maxTimestamp(current.readAt, candidate.readAt),
    deletedAt,
    deletedForDeviceIds: [...new Set([
      ...(current.deletedForDeviceIds ?? []),
      ...(candidate.deletedForDeviceIds ?? [])
    ])],
    updatedAt: maxTimestamp(latest.updatedAt ?? latest.timestamp, deletedAt) ?? latest.timestamp
  });
}

export function normalizeMessage(message: Message): Message {
  const updatedAt = message.updatedAt || message.timestamp;
  const attachments = canonicalAttachments(message);
  const normalized: Message = {
    ...message,
    attachment: attachments[0],
    attachments,
    reactions: message.reactions ?? [],
    replyToMessageId: message.replyToMessageId ?? null,
    editedAt: message.editedAt ?? null,
    deletedAt: message.deletedAt ?? null,
    deletedForDeviceIds: message.deletedForDeviceIds ?? [],
    updatedAt
  };
  if (!normalized.deletedAt) return normalized;
  return {
    ...normalized,
    text: "",
    attachment: undefined,
    attachments: [],
    emphasisLevel: 0,
    reactions: [],
    replyToMessageId: null,
    editedAt: null,
    updatedAt: maxTimestamp(updatedAt, normalized.deletedAt) ?? updatedAt
  };
}

function maxTimestamp(first?: string | null, second?: string | null): string | undefined {
  if (!first) return second ?? undefined;
  if (!second) return first;
  return first >= second ? first : second;
}

function statusRank(status: Message["status"]): number {
  if (status === "read") return 4;
  if (status === "delivered") return 3;
  if (status === "sent") return 2;
  if (status === "pending") return 1;
  return 0;
}

function mutationKey(message: Message): string {
  const attachment = canonicalAttachments(message)
    .map((item) => `${item.id}:${item.name}:${item.mimeType}:${item.size}:${item.width ?? ""}x${item.height ?? ""}`)
    .join(";");
  const reactions = [...(message.reactions ?? [])]
    .sort((left, right) => left.emoji < right.emoji ? -1 : left.emoji > right.emoji ? 1 : 0)
    .map((reaction) => `${reaction.emoji}:${[...reaction.reactorDeviceIds].sort().join(",")}`)
    .join(";");
  return [
    message.deletedAt ?? "",
    message.text,
    attachment,
    message.emphasisLevel ?? 0,
    message.replyToMessageId ?? "",
    message.editedAt ?? "",
    reactions
  ].join("|");
}

function compareMessages(left: Message, right: Message): number {
  return left.timestamp.localeCompare(right.timestamp) || left.id.localeCompare(right.id);
}

function fnv1a(value: string, seed: number): string {
  let hash = seed | 0;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}
