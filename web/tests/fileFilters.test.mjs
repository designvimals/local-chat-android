import assert from "node:assert/strict";
import test from "node:test";
import { categorizeFile, fileMatchesFilter } from "../src/lib/fileFilters.ts";

function file(name, mimeType = null) {
  return { name, path: `/files/${name}`, type: "file", size: 1, mimeType, lastModified: null };
}

test("categorizes common MIME types and Android apps", () => {
  assert.equal(categorizeFile(file("photo.bin", "image/jpeg")), "images");
  assert.equal(categorizeFile(file("clip.bin", "video/mp4")), "videos");
  assert.equal(categorizeFile(file("song.bin", "audio/mpeg")), "audio");
  assert.equal(categorizeFile(file("report.bin", "application/pdf")), "documents");
  assert.equal(categorizeFile(file("backup.bin", "application/zip")), "archives");
  assert.equal(categorizeFile(file("between.bin", "application/vnd.android.package-archive")), "apps");
});

test("falls back to case-insensitive extensions when MIME type is unavailable", () => {
  assert.equal(categorizeFile(file("portrait.HEIC")), "images");
  assert.equal(categorizeFile(file("recording.MP4")), "videos");
  assert.equal(categorizeFile(file("voice-note.M4A")), "audio");
  assert.equal(categorizeFile(file("notes.MD")), "documents");
  assert.equal(categorizeFile(file("photos.7Z")), "archives");
  assert.equal(categorizeFile(file("testing.XAPK")), "apps");
  assert.equal(categorizeFile(file("unknown.data")), "other");
});

test("folders stay visible for every filter", () => {
  const folder = { name: "Camera", path: "/Camera", type: "folder", size: null, mimeType: null, lastModified: null };
  assert.equal(fileMatchesFilter(folder, "images"), true);
  assert.equal(fileMatchesFilter(file("notes.txt"), "images"), false);
  assert.equal(fileMatchesFilter(file("notes.txt"), "documents"), true);
});
