import assert from "node:assert/strict";
import test from "node:test";
import { strFromU8 } from "fflate";
import { createChatExportFileName, createChatExportFiles } from "../src/lib/chatExport.ts";

const exportedAt = new Date("2026-07-17T12:30:00.000Z");
const messages = [
  {
    id: "message-2",
    senderDeviceId: "viewer-web",
    receiverDeviceId: "phone",
    text: "Sent second",
    timestamp: "2026-07-17T12:02:00.000Z",
    status: "read",
    readAt: "2026-07-17T12:03:00.000Z"
  },
  {
    id: "message-1",
    senderDeviceId: "phone",
    receiverDeviceId: "viewer-web",
    text: "Received first",
    timestamp: "2026-07-17T12:01:00.000Z",
    status: "delivered",
    attachment: {
      id: "attachment-1",
      name: "photo.jpg",
      mimeType: "image/jpeg",
      size: 1234
    }
  }
];

test("chat export contains a readable ordered transcript and exact JSON metadata", () => {
  const files = createChatExportFiles(messages, {
    friendName: "Phone",
    viewerDeviceId: "viewer-web",
    exportedAt
  });
  const transcript = strFromU8(files["transcript.txt"]);
  const json = JSON.parse(strFromU8(files["messages.json"]));

  assert.ok(transcript.indexOf("Received first") < transcript.indexOf("Sent second"));
  assert.match(transcript, /Attachment: photo\.jpg/);
  assert.match(transcript, /attachment file contents are not included/i);
  assert.equal(json.format, "between-chat-export");
  assert.equal(json.attachmentFilesIncluded, false);
  assert.deepEqual(json.messages.map(({ id }) => id), ["message-1", "message-2"]);
  assert.equal(json.messages[0].attachment.name, "photo.jpg");
});

test("chat export filename is dated and safe for Windows", () => {
  assert.equal(
    createChatExportFileName(' Sam:Phone / Test? ', exportedAt),
    "between-chat-Sam-Phone-Test-2026-07-17.zip"
  );
});
