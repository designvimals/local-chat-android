import type { FileItem } from "../types/api";
import type { ArchiveFileItem } from "./archivePlan";

export type DownloadBatchMode = "individual" | "zip";

export interface DownloadBatch {
  version: 1;
  id: string;
  mode: DownloadBatchMode;
  folderPath: string;
  files: ArchiveFileItem[];
  nextIndex: number;
  nextPart?: number;
  createdAt: string;
}

interface DownloadBatchManifest {
  version: 1;
  id: string;
  mode: DownloadBatchMode;
  folderPath: string;
  files: ArchiveFileItem[];
  createdAt: string;
}

interface DownloadBatchProgress {
  batchId: string;
  nextIndex: number;
  nextPart?: number;
}

export interface DownloadProgressCheckpoint {
  nextIndex: number;
  nextPart?: number;
}

export interface DownloadQueueStore {
  loadBatch(ownerId: string): Promise<{ manifest: unknown; progress: unknown } | null>;
  saveBatch(
    ownerId: string,
    manifest: DownloadBatchManifest,
    progress: DownloadBatchProgress
  ): Promise<void>;
  saveProgress(ownerId: string, progress: DownloadBatchProgress): Promise<void>;
  clear(ownerId: string): Promise<void>;
}

type LegacyDownloadQueueStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

interface DownloadQueueOptions {
  store?: DownloadQueueStore;
  legacyStorage?: LegacyDownloadQueueStorage | null;
}

const DATABASE_NAME = "between-download-queue";
const DATABASE_VERSION = 1;
const MANIFEST_STORE = "manifests";
const PROGRESS_STORE = "progress";
const LEGACY_STORAGE_PREFIX = "between.download-batch.v1.";

let defaultStore: DownloadQueueStore | null = null;

export function createDownloadBatch(
  mode: DownloadBatchMode,
  folderPath: string,
  files: Array<FileItem | ArchiveFileItem>
): DownloadBatch {
  return {
    version: 1,
    id: crypto.randomUUID(),
    mode,
    folderPath,
    files: files.map(normalizeArchiveFile),
    nextIndex: 0,
    nextPart: mode === "zip" ? 0 : undefined,
    createdAt: new Date().toISOString()
  };
}

export async function loadDownloadBatch(
  ownerId: string,
  options: DownloadQueueOptions = {}
): Promise<DownloadBatch | null> {
  const store = options.store ?? getDefaultStore();
  const legacyStorage = options.legacyStorage === undefined ? getLegacyStorage() : options.legacyStorage;

  try {
    const record = await store.loadBatch(ownerId);
    if (record) {
      const batch = combineBatch(record.manifest, record.progress);
      if (batch && (batch.mode === "zip" || batch.nextIndex < batch.files.length)) return batch;
      await store.clear(ownerId);
    }
  } catch {
    // IndexedDB can be unavailable in hardened private browsing modes.
  }

  const legacyBatch = loadLegacyBatch(ownerId, legacyStorage);
  if (!legacyBatch) return null;
  if (legacyBatch.mode === "individual" && legacyBatch.nextIndex >= legacyBatch.files.length) {
    legacyStorage?.removeItem(legacyStorageKey(ownerId));
    return null;
  }

  try {
    await store.saveBatch(ownerId, manifestFromBatch(legacyBatch), progressFromBatch(legacyBatch));
    legacyStorage?.removeItem(legacyStorageKey(ownerId));
  } catch {
    // Keep the legacy record if migration is not possible.
  }
  return legacyBatch;
}

export async function saveDownloadBatch(
  ownerId: string,
  batch: DownloadBatch,
  options: DownloadQueueOptions = {}
): Promise<void> {
  const store = options.store ?? getDefaultStore();
  const legacyStorage = options.legacyStorage === undefined ? getLegacyStorage() : options.legacyStorage;
  try {
    await store.saveBatch(ownerId, manifestFromBatch(batch), progressFromBatch(batch));
    legacyStorage?.removeItem(legacyStorageKey(ownerId));
  } catch {
    saveLegacyBatch(ownerId, batch, legacyStorage);
  }
}

export async function saveDownloadProgress(
  ownerId: string,
  batchId: string,
  checkpoint: DownloadProgressCheckpoint,
  options: DownloadQueueOptions = {}
): Promise<void> {
  const store = options.store ?? getDefaultStore();
  try {
    await store.saveProgress(ownerId, { batchId, ...checkpoint });
  } catch {
    const legacyStorage = options.legacyStorage === undefined ? getLegacyStorage() : options.legacyStorage;
    const legacyBatch = loadLegacyBatch(ownerId, legacyStorage);
    if (legacyBatch?.id === batchId) {
      saveLegacyBatch(ownerId, { ...legacyBatch, ...checkpoint }, legacyStorage);
    }
  }
}

export async function clearDownloadBatch(
  ownerId: string,
  options: DownloadQueueOptions = {}
): Promise<void> {
  const store = options.store ?? getDefaultStore();
  try {
    await store.clear(ownerId);
  } catch {
    // The legacy cleanup below still runs when IndexedDB is unavailable.
  }
  const legacyStorage = options.legacyStorage === undefined ? getLegacyStorage() : options.legacyStorage;
  legacyStorage?.removeItem(legacyStorageKey(ownerId));
}

class IndexedDbDownloadQueueStore implements DownloadQueueStore {
  private databasePromise: Promise<IDBDatabase> | null = null;

  async loadBatch(ownerId: string): Promise<{ manifest: unknown; progress: unknown } | null> {
    const database = await this.openDatabase();
    const transaction = database.transaction([MANIFEST_STORE, PROGRESS_STORE], "readonly");
    const completed = transactionCompleted(transaction);
    const manifestRequest = transaction.objectStore(MANIFEST_STORE).get(ownerId);
    const progressRequest = transaction.objectStore(PROGRESS_STORE).get(ownerId);
    const [manifestRecord, progressRecord] = await Promise.all([
      requestResult<{ ownerId: string; value: unknown } | undefined>(manifestRequest),
      requestResult<{ ownerId: string; value: unknown } | undefined>(progressRequest)
    ]);
    await completed;
    if (!manifestRecord || !progressRecord) return null;
    return { manifest: manifestRecord.value, progress: progressRecord.value };
  }

  async saveBatch(
    ownerId: string,
    manifest: DownloadBatchManifest,
    progress: DownloadBatchProgress
  ): Promise<void> {
    const database = await this.openDatabase();
    const transaction = database.transaction([MANIFEST_STORE, PROGRESS_STORE], "readwrite");
    const completed = transactionCompleted(transaction);
    transaction.objectStore(MANIFEST_STORE).put({ ownerId, value: manifest });
    transaction.objectStore(PROGRESS_STORE).put({ ownerId, value: progress });
    await completed;
  }

  async saveProgress(ownerId: string, progress: DownloadBatchProgress): Promise<void> {
    const database = await this.openDatabase();
    const transaction = database.transaction(PROGRESS_STORE, "readwrite");
    const completed = transactionCompleted(transaction);
    transaction.objectStore(PROGRESS_STORE).put({ ownerId, value: progress });
    await completed;
  }

  async clear(ownerId: string): Promise<void> {
    const database = await this.openDatabase();
    const transaction = database.transaction([MANIFEST_STORE, PROGRESS_STORE], "readwrite");
    const completed = transactionCompleted(transaction);
    transaction.objectStore(MANIFEST_STORE).delete(ownerId);
    transaction.objectStore(PROGRESS_STORE).delete(ownerId);
    await completed;
  }

  private openDatabase(): Promise<IDBDatabase> {
    if (this.databasePromise) return this.databasePromise;
    this.databasePromise = new Promise((resolve, reject) => {
      if (!("indexedDB" in globalThis)) {
        reject(new Error("IndexedDB is unavailable."));
        return;
      }
      const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
      request.addEventListener("upgradeneeded", () => {
        const database = request.result;
        if (!database.objectStoreNames.contains(MANIFEST_STORE)) {
          database.createObjectStore(MANIFEST_STORE, { keyPath: "ownerId" });
        }
        if (!database.objectStoreNames.contains(PROGRESS_STORE)) {
          database.createObjectStore(PROGRESS_STORE, { keyPath: "ownerId" });
        }
      });
      request.addEventListener("success", () => resolve(request.result), { once: true });
      request.addEventListener("error", () => reject(request.error ?? new Error("Could not open download queue.")), { once: true });
      request.addEventListener("blocked", () => reject(new Error("Download queue upgrade is blocked.")), { once: true });
    });
    return this.databasePromise;
  }
}

function getDefaultStore(): DownloadQueueStore {
  defaultStore ??= new IndexedDbDownloadQueueStore();
  return defaultStore;
}

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.addEventListener("success", () => resolve(request.result), { once: true });
    request.addEventListener("error", () => reject(request.error ?? new Error("Download queue request failed.")), { once: true });
  });
}

function transactionCompleted(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.addEventListener("complete", () => resolve(), { once: true });
    transaction.addEventListener("abort", () => reject(transaction.error ?? new Error("Download queue transaction aborted.")), { once: true });
    transaction.addEventListener("error", () => reject(transaction.error ?? new Error("Download queue transaction failed.")), { once: true });
  });
}

function manifestFromBatch(batch: DownloadBatch): DownloadBatchManifest {
  const { nextIndex: _nextIndex, nextPart: _nextPart, ...manifest } = batch;
  return manifest;
}

function progressFromBatch(batch: DownloadBatch): DownloadBatchProgress {
  return { batchId: batch.id, nextIndex: batch.nextIndex, nextPart: batch.nextPart };
}

function combineBatch(manifestValue: unknown, progressValue: unknown): DownloadBatch | null {
  if (!isDownloadBatchManifest(manifestValue) || !isDownloadBatchProgress(progressValue)) return null;
  if (progressValue.batchId !== manifestValue.id || progressValue.nextIndex > manifestValue.files.length) return null;
  return {
    ...manifestValue,
    files: manifestValue.files.map(normalizeArchiveFile),
    nextIndex: progressValue.nextIndex,
    nextPart: progressValue.nextPart
  };
}

function getLegacyStorage(): LegacyDownloadQueueStorage | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function loadLegacyBatch(
  ownerId: string,
  storage: LegacyDownloadQueueStorage | null
): DownloadBatch | null {
  if (!storage) return null;
  const key = legacyStorageKey(ownerId);
  try {
    const raw = storage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as unknown;
    if (!isDownloadBatch(parsed)) throw new Error("Invalid legacy download batch.");
    return {
      ...parsed,
      files: parsed.files.map(normalizeArchiveFile)
    };
  } catch {
    try {
      storage.removeItem(key);
    } catch {
      // Storage can be fully blocked in hardened private browsing modes.
    }
    return null;
  }
}

function saveLegacyBatch(
  ownerId: string,
  batch: DownloadBatch,
  storage: LegacyDownloadQueueStorage | null
): void {
  try {
    storage?.setItem(legacyStorageKey(ownerId), JSON.stringify(batch));
  } catch {
    // Downloads can continue even when persistence is unavailable.
  }
}

function legacyStorageKey(ownerId: string): string {
  return `${LEGACY_STORAGE_PREFIX}${encodeURIComponent(ownerId)}`;
}

function isDownloadBatch(value: unknown): value is DownloadBatch {
  if (!isRecord(value) || !isDownloadBatchManifest(value)) return false;
  return Number.isInteger(value.nextIndex) &&
    typeof value.nextIndex === "number" &&
    value.nextIndex >= 0 &&
    value.nextIndex <= value.files.length;
}

function isDownloadBatchManifest(value: unknown): value is DownloadBatchManifest {
  if (!isRecord(value)) return false;
  if (value.version !== 1 || (value.mode !== "individual" && value.mode !== "zip")) return false;
  if (typeof value.id !== "string" || typeof value.folderPath !== "string" || typeof value.createdAt !== "string") {
    return false;
  }
  return Array.isArray(value.files) && value.files.length > 0 && value.files.every(isFileItem);
}

function isDownloadBatchProgress(value: unknown): value is DownloadBatchProgress {
  if (!isRecord(value)) return false;
  return typeof value.batchId === "string" &&
    typeof value.nextIndex === "number" &&
    Number.isInteger(value.nextIndex) &&
    value.nextIndex >= 0 &&
    (value.nextPart === undefined || (
      typeof value.nextPart === "number" && Number.isInteger(value.nextPart) && value.nextPart >= 0
    ));
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

function normalizeArchiveFile(file: FileItem | ArchiveFileItem): ArchiveFileItem {
  if (file.type !== "file") throw new Error("Download batches can only contain files.");
  const archivePath = "archivePath" in file && typeof file.archivePath === "string" && file.archivePath.trim()
    ? file.archivePath
    : file.name;
  return { ...file, type: "file", archivePath };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
