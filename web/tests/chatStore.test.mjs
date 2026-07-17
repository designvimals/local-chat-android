import assert from "node:assert/strict";
import test from "node:test";
import { filterDeletedMessages, mergeMessages } from "../src/lib/chatStore.ts";

function message(id, text = id) {
  return {
    id,
    senderDeviceId: "phone",
    receiverDeviceId: "web",
    text,
    timestamp: `2026-01-01T00:00:0${id}.000Z`,
    status: "delivered"
  };
}

test("deleted message ids remain filtered after a remote sync", () => {
  const deletedIds = new Set(["2"]);
  const merged = mergeMessages([message("1")], [message("2"), message("3")]);

  assert.deepEqual(filterDeletedMessages(merged, deletedIds).map(({ id }) => id), ["1", "3"]);
});

test("removing a tombstone makes the cached message visible again for undo", () => {
  const deletedIds = new Set(["2"]);
  deletedIds.delete("2");

  assert.deepEqual(filterDeletedMessages([message("2")], deletedIds).map(({ id }) => id), ["2"]);
});
