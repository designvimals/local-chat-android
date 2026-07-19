export interface ArchiveStagingStore {
  load(key: string): Promise<Blob | null>;
  save(record: StagedArchiveRecord): Promise<void>;
  deleteKeys(keys: string[]): Promise<void>;
  clearBatch(ownerId: string, batchId: string): Promise<void>;
  clearOwner(ownerId: string): Promise<void>;
}

interface ArchiveStagingOptions {
  store?: ArchiveStagingStore;
}

interface StagedArchiveRecord {
  key: string;
  ownerId: string;
  batchId: string;
  index: number;
  blob: Blob;
}

const DATABASE_NAME = "between-archive-staging";
const DATABASE_VERSION = 1;
const FILE_STORE = "files";
const BATCH_INDEX = "batch";
const OWNER_INDEX = "owner";

let defaultStore: ArchiveStagingStore | null = null;

export async function loadStagedArchiveFile(
  ownerId: string,
  batchId: string,
  index: number,
  options: ArchiveStagingOptions = {}
): Promise<Blob | null> {
  return (options.store ?? getDefaultStore()).load(stagingKey(ownerId, batchId, index));
}

export async function saveStagedArchiveFile(
  ownerId: string,
  batchId: string,
  index: number,
  blob: Blob,
  options: ArchiveStagingOptions = {}
): Promise<void> {
  await (options.store ?? getDefaultStore()).save({
    key: stagingKey(ownerId, batchId, index),
    ownerId,
    batchId,
    index,
    blob
  });
}

export async function deleteStagedArchiveFiles(
  ownerId: string,
  batchId: string,
  indexes: number[],
  options: ArchiveStagingOptions = {}
): Promise<void> {
  await (options.store ?? getDefaultStore()).deleteKeys(
    indexes.map((index) => stagingKey(ownerId, batchId, index))
  );
}

export async function clearStagedArchiveFiles(
  ownerId: string,
  batchId?: string,
  options: ArchiveStagingOptions = {}
): Promise<void> {
  const store = options.store ?? getDefaultStore();
  if (batchId) await store.clearBatch(ownerId, batchId);
  else await store.clearOwner(ownerId);
}

export async function requestPersistentArchiveStorage(): Promise<boolean> {
  try {
    return await navigator.storage?.persist?.() ?? false;
  } catch {
    return false;
  }
}

class IndexedDbArchiveStagingStore implements ArchiveStagingStore {
  private databasePromise: Promise<IDBDatabase> | null = null;

  async load(key: string): Promise<Blob | null> {
    const database = await this.openDatabase();
    const transaction = database.transaction(FILE_STORE, "readonly");
    const request = transaction.objectStore(FILE_STORE).get(key);
    const record = await requestResult<StagedArchiveRecord | undefined>(request);
    await transactionCompleted(transaction);
    return record?.blob ?? null;
  }

  async save(record: StagedArchiveRecord): Promise<void> {
    const database = await this.openDatabase();
    const transaction = database.transaction(FILE_STORE, "readwrite");
    const completed = transactionCompleted(transaction);
    transaction.objectStore(FILE_STORE).put(record);
    await completed;
  }

  async deleteKeys(keys: string[]): Promise<void> {
    if (keys.length === 0) return;
    const database = await this.openDatabase();
    const transaction = database.transaction(FILE_STORE, "readwrite");
    const completed = transactionCompleted(transaction);
    const store = transaction.objectStore(FILE_STORE);
    for (const key of keys) store.delete(key);
    await completed;
  }

  async clearBatch(ownerId: string, batchId: string): Promise<void> {
    await this.clearIndex(BATCH_INDEX, IDBKeyRange.only([ownerId, batchId]));
  }

  async clearOwner(ownerId: string): Promise<void> {
    await this.clearIndex(OWNER_INDEX, IDBKeyRange.only(ownerId));
  }

  private async clearIndex(indexName: string, range: IDBKeyRange): Promise<void> {
    const database = await this.openDatabase();
    const readTransaction = database.transaction(FILE_STORE, "readonly");
    const keys = await requestResult<IDBValidKey[]>(
      readTransaction.objectStore(FILE_STORE).index(indexName).getAllKeys(range)
    );
    await transactionCompleted(readTransaction);
    await this.deleteKeys(keys.map(String));
  }

  private openDatabase(): Promise<IDBDatabase> {
    if (this.databasePromise) return this.databasePromise;
    this.databasePromise = new Promise((resolve, reject) => {
      if (!("indexedDB" in globalThis)) {
        reject(new Error("Persistent browser storage is unavailable."));
        return;
      }
      const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
      request.addEventListener("upgradeneeded", () => {
        const database = request.result;
        if (database.objectStoreNames.contains(FILE_STORE)) return;
        const store = database.createObjectStore(FILE_STORE, { keyPath: "key" });
        store.createIndex(BATCH_INDEX, ["ownerId", "batchId"], { unique: false });
        store.createIndex(OWNER_INDEX, "ownerId", { unique: false });
      });
      request.addEventListener("success", () => resolve(request.result), { once: true });
      request.addEventListener("error", () => reject(request.error ?? new Error("Could not open archive storage.")), { once: true });
      request.addEventListener("blocked", () => reject(new Error("Archive storage is blocked by another tab.")), { once: true });
    });
    return this.databasePromise;
  }
}

function getDefaultStore(): ArchiveStagingStore {
  defaultStore ??= new IndexedDbArchiveStagingStore();
  return defaultStore;
}

function stagingKey(ownerId: string, batchId: string, index: number): string {
  return JSON.stringify([ownerId, batchId, index]);
}

function requestResult<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.addEventListener("success", () => resolve(request.result), { once: true });
    request.addEventListener("error", () => reject(request.error ?? new Error("Archive storage request failed.")), { once: true });
  });
}

function transactionCompleted(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.addEventListener("complete", () => resolve(), { once: true });
    transaction.addEventListener("abort", () => reject(transaction.error ?? new Error("Archive storage transaction aborted.")), { once: true });
    transaction.addEventListener("error", () => reject(transaction.error ?? new Error("Archive storage transaction failed.")), { once: true });
  });
}
