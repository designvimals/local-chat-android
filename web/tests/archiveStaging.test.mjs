import assert from "node:assert/strict";
import test from "node:test";
import {
  clearStagedArchiveFiles,
  deleteStagedArchiveFiles,
  loadStagedArchiveFile,
  saveStagedArchiveFile
} from "../src/lib/archiveStaging.ts";

function memoryStagingStore() {
  const records = new Map();
  return {
    async load(key) { return records.get(key)?.blob ?? null; },
    async save(record) { records.set(record.key, record); },
    async deleteKeys(keys) { for (const key of keys) records.delete(key); },
    async clearBatch(ownerId, batchId) {
      for (const [key, record] of records) {
        if (record.ownerId === ownerId && record.batchId === batchId) records.delete(key);
      }
    },
    async clearOwner(ownerId) {
      for (const [key, record] of records) {
        if (record.ownerId === ownerId) records.delete(key);
      }
    },
    records
  };
}

test("staged blobs survive independent queue operations", async () => {
  const store = memoryStagingStore();
  const options = { store };
  await saveStagedArchiveFile("viewer", "batch", 0, new Blob(["one"]), options);
  await saveStagedArchiveFile("viewer", "batch", 1, new Blob(["two"]), options);

  assert.equal(await (await loadStagedArchiveFile("viewer", "batch", 0, options)).text(), "one");
  assert.equal(await (await loadStagedArchiveFile("viewer", "batch", 1, options)).text(), "two");
});

test("completed parts and discarded batches remove only their staged blobs", async () => {
  const store = memoryStagingStore();
  const options = { store };
  await saveStagedArchiveFile("viewer", "first", 0, new Blob(["one"]), options);
  await saveStagedArchiveFile("viewer", "first", 1, new Blob(["two"]), options);
  await saveStagedArchiveFile("viewer", "second", 0, new Blob(["other"]), options);

  await deleteStagedArchiveFiles("viewer", "first", [0], options);
  assert.equal(await loadStagedArchiveFile("viewer", "first", 0, options), null);
  assert.notEqual(await loadStagedArchiveFile("viewer", "first", 1, options), null);

  await clearStagedArchiveFiles("viewer", "first", options);
  assert.equal(await loadStagedArchiveFile("viewer", "first", 1, options), null);
  assert.notEqual(await loadStagedArchiveFile("viewer", "second", 0, options), null);

  await clearStagedArchiveFiles("viewer", undefined, options);
  assert.equal(store.records.size, 0);
});
