import assert from "node:assert/strict";
import test from "node:test";
import {
  archivePartBounds,
  collectSelectedArchiveFiles,
  inferCompletedArchiveParts,
  relativeArchivePath,
  splitArchiveParts
} from "../src/lib/archivePlan.ts";

function item(name, path, type = "file", size = 1) {
  return {
    name,
    path,
    type,
    size: type === "file" ? size : null,
    mimeType: type === "file" ? "application/octet-stream" : null,
    lastModified: null
  };
}

test("selected folders are collected recursively with their relative paths", async () => {
  const tree = new Map([
    ["/Photos", [
      item("Trips", "/Photos/Trips", "folder"),
      item("cover.jpg", "/Photos/cover.jpg", "file", 10)
    ]],
    ["/Photos/Trips", [item("beach.jpg", "/Photos/Trips/beach.jpg", "file", 20)]],
    ["/Docs", [item("notes.txt", "/Docs/notes.txt", "file", 5)]]
  ]);
  const progress = [];

  const files = await collectSelectedArchiveFiles(
    [item("Photos", "/Photos", "folder"), item("Docs", "/Docs", "folder")],
    "/",
    async (path) => tree.get(path) ?? [],
    { onProgress: (value) => progress.push(value) }
  );

  assert.deepEqual(files.map((file) => file.archivePath), [
    "Docs/notes.txt",
    "Photos/cover.jpg",
    "Photos/Trips/beach.jpg"
  ]);
  assert.equal(progress.at(-1).foldersScanned, 3);
});

test("overlapping folder and file selections are deduplicated", async () => {
  const photo = item("cover.jpg", "/Photos/cover.jpg", "file");
  const files = await collectSelectedArchiveFiles(
    [item("Photos", "/Photos", "folder"), photo],
    "/",
    async () => [photo]
  );

  assert.deepEqual(files.map((file) => file.archivePath), ["Photos/cover.jpg"]);
});

test("archive part boundaries support resumable checkpoints", () => {
  const files = Array.from({ length: 6 }, (_, index) => ({
    ...item(`${index}.bin`, `/${index}.bin`, "file", 1),
    archivePath: `${index}.bin`
  }));
  const parts = splitArchiveParts(files, 2, 100);

  assert.deepEqual(parts.map((part) => part.length), [2, 2, 2]);
  assert.deepEqual(archivePartBounds(parts, 1), { start: 2, end: 4 });
  assert.equal(inferCompletedArchiveParts(parts, 3), 1);
  assert.equal(inferCompletedArchiveParts(parts, 4), 2);
});

test("relative archive paths fall back safely when a file is outside the base", () => {
  assert.equal(relativeArchivePath("/Elsewhere/photo.jpg", "/Photos", "photo.jpg"), "photo.jpg");
});
