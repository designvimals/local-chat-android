import assert from "node:assert/strict";
import test from "node:test";
import { fileMatchesSearch, normalizeSearchText } from "../src/lib/fileSearch.ts";

function item(name, type = "file") {
  return {
    name,
    path: `/${name}`,
    type,
    size: type === "file" ? 1 : null,
    mimeType: type === "file" ? "application/octet-stream" : null,
    lastModified: null
  };
}

test("filename search is case insensitive and Unicode normalized", () => {
  assert.equal(fileMatchesSearch(item("Caf\u00e9 Photo.JPG"), "cafe\u0301 photo"), true);
  assert.equal(normalizeSearchText("  REPORT.PDF "), "report.pdf");
});

test("all search terms must appear in the item name in any order", () => {
  assert.equal(fileMatchesSearch(item("summer-family-photo.jpg"), "photo summer"), true);
  assert.equal(fileMatchesSearch(item("summer-family-photo.jpg"), "photo winter"), false);
});

test("search includes folders and an empty query matches everything", () => {
  assert.equal(fileMatchesSearch(item("Camera", "folder"), "cam"), true);
  assert.equal(fileMatchesSearch(item("notes.txt"), "   "), true);
});
