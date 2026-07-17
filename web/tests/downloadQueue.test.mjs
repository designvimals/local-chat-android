import assert from "node:assert/strict";
import test from "node:test";
import {
  clearDownloadBatch,
  loadDownloadBatch,
  saveDownloadBatch,
  saveDownloadProgress
} from "../src/lib/downloadQueue.ts";

function memoryLegacyStorage() {
  const values = new Map();
  return {
    getItem(key) { return values.get(key) ?? null; },
    setItem(key, value) { values.set(key, value); },
    removeItem(key) { values.delete(key); },
    values
  };
}

function memoryQueueStore() {
  const manifests = new Map();
  const progress = new Map();
  let manifestWrites = 0;
  return {
    async loadBatch(ownerId) {
      if (!manifests.has(ownerId) || !progress.has(ownerId)) return null;
      return { manifest: manifests.get(ownerId), progress: progress.get(ownerId) };
    },
    async saveBatch(ownerId, manifest, nextProgress) {
      manifests.set(ownerId, structuredClone(manifest));
      progress.set(ownerId, structuredClone(nextProgress));
      manifestWrites += 1;
    },
    async saveProgress(ownerId, nextProgress) {
      progress.set(ownerId, structuredClone(nextProgress));
    },
    async clear(ownerId) {
      manifests.delete(ownerId);
      progress.delete(ownerId);
    },
    get manifestWrites() { return manifestWrites; },
    manifests,
    progress
  };
}

function batch(overrides = {}) {
  return {
    version: 1,
    id: "batch-one",
    mode: "individual",
    folderPath: "/Downloads",
    files: [
      {
        name: "one.txt",
        path: "/Downloads/one.txt",
        type: "file",
        size: 3,
        mimeType: "text/plain",
        lastModified: null
      },
      {
        name: "two.txt",
        path: "/Downloads/two.txt",
        type: "file",
        size: 3,
        mimeType: "text/plain",
        lastModified: null
      }
    ],
    nextIndex: 0,
    createdAt: "2026-07-15T10:00:00Z",
    ...overrides
  };
}

test("progress updates do not rewrite the saved file manifest", async () => {
  const store = memoryQueueStore();
  const options = { store, legacyStorage: null };
  await saveDownloadBatch("viewer-one", batch(), options);
  await saveDownloadProgress("viewer-one", "batch-one", 1, options);

  assert.equal(store.manifestWrites, 1);
  assert.equal((await loadDownloadBatch("viewer-one", options))?.nextIndex, 1);
});

test("a saved queue remains isolated to its paired browser", async () => {
  const store = memoryQueueStore();
  const options = { store, legacyStorage: null };
  await saveDownloadBatch("viewer-one", batch({ nextIndex: 1 }), options);

  assert.equal((await loadDownloadBatch("viewer-one", options))?.nextIndex, 1);
  assert.equal(await loadDownloadBatch("viewer-two", options), null);
});

test("a completed queue is removed instead of being resumed", async () => {
  const store = memoryQueueStore();
  const options = { store, legacyStorage: null };
  await saveDownloadBatch("viewer-one", batch({ nextIndex: 2 }), options);

  assert.equal(await loadDownloadBatch("viewer-one", options), null);
  assert.equal(store.manifests.has("viewer-one"), false);
});

test("legacy localStorage queues migrate into the split async store", async () => {
  const store = memoryQueueStore();
  const legacyStorage = memoryLegacyStorage();
  legacyStorage.setItem("between.download-batch.v1.viewer-one", JSON.stringify(batch({ nextIndex: 1 })));

  const restored = await loadDownloadBatch("viewer-one", { store, legacyStorage });

  assert.equal(restored?.nextIndex, 1);
  assert.equal(store.manifestWrites, 1);
  assert.equal(legacyStorage.values.size, 0);
});

test("discard clears the manifest, progress, and legacy fallback", async () => {
  const store = memoryQueueStore();
  const legacyStorage = memoryLegacyStorage();
  const options = { store, legacyStorage };
  await saveDownloadBatch("viewer-one", batch(), options);
  legacyStorage.setItem("between.download-batch.v1.viewer-one", JSON.stringify(batch()));

  await clearDownloadBatch("viewer-one", options);

  assert.equal(await loadDownloadBatch("viewer-one", options), null);
  assert.equal(store.manifests.has("viewer-one"), false);
  assert.equal(legacyStorage.values.size, 0);
});
