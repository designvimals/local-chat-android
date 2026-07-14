import assert from "node:assert/strict";
import test from "node:test";
import { canonicalAttachments, mergeMessages, normalizeMessage } from "../src/lib/chatStore.ts";
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
