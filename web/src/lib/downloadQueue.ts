import type { FileItem } from "../types/api";

export type DownloadBatchMode = "individual" | "zip";

export interface DownloadBatch {
  version: 1;
  id: string;
  mode: DownloadBatchMode;
  folderPath: string;
  files: FileItem[];
  nextIndex: number;
  createdAt: string;
}

type DownloadQueueStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

const STORAGE_PREFIX = "between.download-batch.v1.";

export function createDownloadBatch(
  mode: DownloadBatchMode,
  folderPath: string,
  files: FileItem[]
): DownloadBatch {
  return {
    version: 1,
    id: crypto.randomUUID(),
    mode,
    folderPath,
    files: files.map((file) => ({ ...file })),
    nextIndex: 0,
    createdAt: new Date().toISOString()
  };
}

export function loadDownloadBatch(
  ownerId: string,
  storage: DownloadQueueStorage = window.localStorage
): DownloadBatch | null {
  const key = storageKey(ownerId);
  const raw = storage.getItem(key);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!isDownloadBatch(parsed)) throw new Error("Invalid saved download batch.");
    if (parsed.nextIndex === parsed.files.length) {
      storage.removeItem(key);
      return null;
    }
    return parsed;
  } catch {
    storage.removeItem(key);
    return null;
  }
}

export function saveDownloadBatch(
  ownerId: string,
  batch: DownloadBatch,
  storage: DownloadQueueStorage = window.localStorage
): void {
  storage.setItem(storageKey(ownerId), JSON.stringify(batch));
}

export function clearDownloadBatch(
  ownerId: string,
  storage: DownloadQueueStorage = window.localStorage
): void {
  storage.removeItem(storageKey(ownerId));
}

function storageKey(ownerId: string): string {
  return `${STORAGE_PREFIX}${encodeURIComponent(ownerId)}`;
}

function isDownloadBatch(value: unknown): value is DownloadBatch {
  if (!isRecord(value)) return false;
  if (value.version !== 1 || (value.mode !== "individual" && value.mode !== "zip")) return false;
  if (typeof value.id !== "string" || typeof value.folderPath !== "string" || typeof value.createdAt !== "string") {
    return false;
  }
  if (!Array.isArray(value.files) || value.files.length === 0 || !value.files.every(isFileItem)) return false;
  return Number.isInteger(value.nextIndex) &&
    typeof value.nextIndex === "number" &&
    value.nextIndex >= 0 &&
    value.nextIndex <= value.files.length;
}

function isFileItem(value: unknown): value is FileItem {
  if (!isRecord(value)) return false;
  return typeof value.name === "string" &&
    typeof value.path === "string" &&
    value.type === "file" &&
    (typeof value.size === "number" || value.size === null) &&
    (typeof value.mimeType === "string" || value.mimeType === null) &&
    (typeof value.lastModified === "string" || value.lastModified === null);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
