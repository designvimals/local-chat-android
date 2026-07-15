import assert from "node:assert/strict";
import test from "node:test";
import {
  clearDownloadBatch,
  loadDownloadBatch,
  saveDownloadBatch
} from "../src/lib/downloadQueue.ts";

function memoryStorage() {
  const values = new Map();
  return {
    getItem(key) { return values.get(key) ?? null; },
    setItem(key, value) { values.set(key, value); },
    removeItem(key) { values.delete(key); },
    values
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

test("download checkpoint survives reload for the same paired browser", () => {
  const storage = memoryStorage();
  saveDownloadBatch("viewer-one", batch({ nextIndex: 1 }), storage);

  assert.equal(loadDownloadBatch("viewer-one", storage)?.nextIndex, 1);
  assert.equal(loadDownloadBatch("viewer-two", storage), null);
});

test("a completed or malformed queue is removed instead of being resumed", () => {
  const storage = memoryStorage();
  saveDownloadBatch("viewer-one", batch({ nextIndex: 2 }), storage);
  assert.equal(loadDownloadBatch("viewer-one", storage), null);

  storage.setItem("between.download-batch.v1.viewer-one", "not-json");
  assert.equal(loadDownloadBatch("viewer-one", storage), null);
  assert.equal(storage.values.size, 0);
});

test("discard clears only the selected browser's queue", () => {
  const storage = memoryStorage();
  saveDownloadBatch("viewer-one", batch(), storage);
  saveDownloadBatch("viewer-two", batch({ id: "batch-two" }), storage);

  clearDownloadBatch("viewer-one", storage);

  assert.equal(loadDownloadBatch("viewer-one", storage), null);
  assert.equal(loadDownloadBatch("viewer-two", storage)?.id, "batch-two");
});
