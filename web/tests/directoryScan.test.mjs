import assert from "node:assert/strict";
import test from "node:test";
import {
  collectDirectoryFileNames,
  filesMissingByName,
  normalizeFileName
} from "../src/lib/directoryScan.ts";

function file(name) {
  return { kind: "file", name };
}

function directory(name, entries) {
  return {
    kind: "directory",
    name,
    async *values() {
      yield* entries;
    }
  };
}

function fileItem(name) {
  return {
    name,
    path: `/Downloads/${name}`,
    type: "file",
    size: 1,
    mimeType: "application/octet-stream",
    lastModified: null
  };
}

test("recursively collects normalized filenames from the chosen directory", async () => {
  const root = directory("Downloads", [
    file("Photo.JPG"),
    directory("archive", [file("notes.txt")])
  ]);

  const names = await collectDirectoryFileNames(root);

  assert.deepEqual(names, new Set(["photo.jpg", "notes.txt"]));
});

test("missing-file comparison is case insensitive and Unicode normalized", () => {
  const composed = "caf\u00e9.txt";
  const decomposed = "cafe\u0301.txt";
  const existing = new Set([normalizeFileName(decomposed), normalizeFileName("PHOTO.JPG")]);

  const missing = filesMissingByName(
    [fileItem(composed), fileItem("photo.jpg"), fileItem("missing.zip")],
    existing
  );

  assert.deepEqual(missing.map((item) => item.name), ["missing.zip"]);
});
