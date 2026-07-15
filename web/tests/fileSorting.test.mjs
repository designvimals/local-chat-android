import assert from "node:assert/strict";
import test from "node:test";
import {
  defaultSortDirection,
  sortDirectionLabel,
  sortFileItems
} from "../src/lib/fileSorting.ts";

function item(name, overrides = {}) {
  return {
    name,
    path: `/${name}`,
    type: "file",
    size: null,
    mimeType: null,
    lastModified: null,
    ...overrides
  };
}

test("sorts names naturally while keeping folders first", () => {
  const sorted = sortFileItems([
    item("file10.txt"),
    item("z-folder", { type: "folder" }),
    item("file2.txt"),
    item("a-folder", { type: "folder" })
  ], "name", "ascending");

  assert.deepEqual(sorted.map((entry) => entry.name), ["a-folder", "z-folder", "file2.txt", "file10.txt"]);
  assert.deepEqual(
    sortFileItems(sorted, "name", "descending").map((entry) => entry.name),
    ["z-folder", "a-folder", "file10.txt", "file2.txt"]
  );
});

test("sorts date newest or oldest and leaves missing dates last", () => {
  const files = [
    item("missing.txt"),
    item("older.txt", { lastModified: "2026-07-10T10:00:00Z" }),
    item("newer.txt", { lastModified: "2026-07-14T10:00:00Z" })
  ];

  assert.deepEqual(
    sortFileItems(files, "date", "descending").map((entry) => entry.name),
    ["newer.txt", "older.txt", "missing.txt"]
  );
  assert.deepEqual(
    sortFileItems(files, "date", "ascending").map((entry) => entry.name),
    ["older.txt", "newer.txt", "missing.txt"]
  );
});

test("sorts size largest or smallest and leaves missing sizes last", () => {
  const files = [item("missing.bin"), item("small.bin", { size: 10 }), item("large.bin", { size: 100 })];
  assert.deepEqual(
    sortFileItems(files, "size", "descending").map((entry) => entry.name),
    ["large.bin", "small.bin", "missing.bin"]
  );
  assert.deepEqual(
    sortFileItems(files, "size", "ascending").map((entry) => entry.name),
    ["small.bin", "large.bin", "missing.bin"]
  );
});

test("uses useful default directions and labels", () => {
  assert.equal(defaultSortDirection("name"), "ascending");
  assert.equal(defaultSortDirection("date"), "descending");
  assert.equal(defaultSortDirection("size"), "descending");
  assert.equal(sortDirectionLabel("name", "ascending"), "A–Z");
  assert.equal(sortDirectionLabel("date", "descending"), "Newest");
  assert.equal(sortDirectionLabel("size", "ascending"), "Smallest");
});
