import assert from "node:assert/strict";
import test from "node:test";
import {
  canonicalAttachments,
  filterDeletedMessages,
  mergeMessages,
  messageSyncRevision,
  normalizeMessage
} from "../src/lib/chatStore.ts";
import { singleEmojiOrNull } from "../src/lib/messagePresentation.ts";

function message(overrides = {}) {
  return {
    id: "one",
    senderDeviceId: "phone",
    receiverDeviceId: "web",
    text: "hello",
    timestamp: "2026-07-14T10:00:00Z",
    status: "sent",
    ...overrides
  };
}

test("offline reconnect cannot resurrect a tombstone", () => {
  const local = message({
    text: "",
    deletedAt: "2026-07-14T10:05:00Z",
    updatedAt: "2026-07-14T10:05:00Z"
  });
  const staleRemote = message({
    text: "resurrected",
    status: "read",
    updatedAt: "2026-07-14T10:06:00Z"
  });

  const [merged] = mergeMessages([local], [staleRemote]);

  assert.equal(merged.text, "");
  assert.equal(merged.deletedAt, local.deletedAt);
  assert.equal(merged.status, "read");
});

test("local deletion ids union independently from content", () => {
  const [merged] = mergeMessages(
    [message({ deletedForDeviceIds: ["phone"] })],
    [message({ text: "edited", editedAt: "2026-07-14T10:02:00Z", updatedAt: "2026-07-14T10:02:00Z", deletedForDeviceIds: ["web"] })]
  );
  assert.equal(merged.text, "edited");
  assert.deepEqual(new Set(merged.deletedForDeviceIds), new Set(["phone", "web"]));
});

test("equal timestamp merge is deterministic", () => {
  const first = message({ text: "alpha", updatedAt: "2026-07-14T10:02:00Z" });
  const second = message({ text: "beta", updatedAt: "2026-07-14T10:02:00Z" });
  assert.deepEqual(mergeMessages([first], [second]), mergeMessages([second], [first]));
});

test("single emoji detection handles grapheme sequences", () => {
  for (const emoji of ["🇮🇳", "1️⃣", "👍🏽", "👨‍👩‍👧‍👦", "❤️"]) {
    assert.equal(singleEmojiOrNull(emoji), emoji);
  }
  for (const value of ["hello", "1", "👍👍", "❤️🔥"]) {
    assert.equal(singleEmojiOrNull(value), null);
  }
});

test("legacy and multi-attachment records normalize to one ordered list", () => {
  const first = { id: "first", name: "first.jpg", mimeType: "image/jpeg", size: 1 };
  const second = { id: "second", name: "second.jpg", mimeType: "image/jpeg", size: 2 };
  const normalized = normalizeMessage(message({
    attachment: first,
    attachments: [first, second, first]
  }));

  assert.deepEqual(canonicalAttachments(normalized).map((item) => item.id), ["first", "second"]);
  assert.equal(normalized.attachment.id, "first");
});

test("a tombstone clears legacy and multi-attachment content", () => {
  const attachment = { id: "first", name: "first.jpg", mimeType: "image/jpeg", size: 1 };
  const normalized = normalizeMessage(message({
    attachment,
    attachments: [attachment],
    deletedAt: "2026-07-14T10:05:00Z"
  }));

  assert.equal(normalized.attachment, undefined);
  assert.deepEqual(normalized.attachments, []);
});

test("sync revisions change for content, receipts, and local deletions", () => {
  const original = message();
  const revision = messageSyncRevision(original);
  assert.equal(revision, "7bbbed98b8f37154");
  assert.notEqual(messageSyncRevision(message({ text: "changed", updatedAt: "2026-07-14T10:01:00Z" })), revision);
  assert.notEqual(messageSyncRevision(message({ status: "read", readAt: "2026-07-14T10:01:00Z" })), revision);
  assert.notEqual(messageSyncRevision(message({ deletedForDeviceIds: ["phone"] })), revision);
  assert.equal(messageSyncRevision({ ...original }), revision);
});

test("deleted message ids remain filtered after a remote sync", () => {
  const deletedIds = new Set(["2"]);
  const merged = mergeMessages(
    [message({ id: "1", timestamp: "2026-01-01T00:00:01.000Z" })],
    [
      message({ id: "2", timestamp: "2026-01-01T00:00:02.000Z" }),
      message({ id: "3", timestamp: "2026-01-01T00:00:03.000Z" })
    ]
  );

  assert.deepEqual(filterDeletedMessages(merged, deletedIds).map(({ id }) => id), ["1", "3"]);
});

test("removing a tombstone makes the cached message visible again for undo", () => {
  const deletedIds = new Set(["2"]);
  deletedIds.delete("2");

  assert.deepEqual(filterDeletedMessages([message({ id: "2" })], deletedIds).map(({ id }) => id), ["2"]);
});
