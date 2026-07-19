import { strToU8, zip, type AsyncZippable } from "fflate";
import type { Message } from "../types/api";

interface ChatExportOptions {
  friendName: string;
  viewerDeviceId: string;
  exportedAt?: Date;
}

interface ChatExportResult {
  blob: Blob;
  fileName: string;
}

export function createChatExportFiles(
  messages: Message[],
  { friendName, viewerDeviceId, exportedAt = new Date() }: ChatExportOptions
): Record<string, Uint8Array> {
  const exportedAtIso = exportedAt.toISOString();
  const orderedMessages = [...messages].sort((left, right) => (
    left.timestamp.localeCompare(right.timestamp) || left.id.localeCompare(right.id)
  ));
  const archive = {
    format: "between-chat-export",
    version: 1,
    exportedAt: exportedAtIso,
    conversation: {
      friendName,
      viewerDeviceId
    },
    attachmentFilesIncluded: false,
    messages: orderedMessages
  };

  return {
    "transcript.txt": strToU8(createReadableTranscript(orderedMessages, friendName, viewerDeviceId, exportedAtIso)),
    "messages.json": strToU8(`${JSON.stringify(archive, null, 2)}\n`)
  };
}

export async function buildChatExport(
  messages: Message[],
  options: ChatExportOptions
): Promise<ChatExportResult> {
  const exportedAt = options.exportedAt ?? new Date();
  const data = await createZip(createChatExportFiles(messages, { ...options, exportedAt }));
  return {
    blob: new Blob([data], { type: "application/zip" }),
    fileName: createChatExportFileName(options.friendName, exportedAt)
  };
}

export function createChatExportFileName(friendName: string, exportedAt: Date): string {
  const safeName = friendName
    .normalize("NFKC")
    .replace(/[<>:"/\\|?*\u0000-\u001f]/g, "-")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^[.\s-]+|[.\s-]+$/g, "")
    .slice(0, 48) || "conversation";
  return `between-chat-${safeName}-${exportedAt.toISOString().slice(0, 10)}.zip`;
}

function createReadableTranscript(
  messages: Message[],
  friendName: string,
  viewerDeviceId: string,
  exportedAtIso: string
): string {
  const lines = [
    "Between chat export",
    `Conversation with: ${friendName}`,
    `Exported at: ${exportedAtIso}`,
    `Messages: ${messages.length}`,
    "Attachments: names and metadata only; attachment file contents are not included.",
    ""
  ];

  for (const message of messages) {
    const sender = message.senderDeviceId === viewerDeviceId ? "You" : friendName;
    lines.push("---");
    lines.push(`[${message.timestamp}] ${sender}`);
    lines.push(`Message ID: ${message.id}`);
    lines.push(`Status: ${message.status}`);
    if (message.deliveredAt) lines.push(`Delivered at: ${message.deliveredAt}`);
    if (message.readAt) lines.push(`Read at: ${message.readAt}`);
    if (message.attachment) {
      lines.push(`Attachment: ${message.attachment.name}`);
      lines.push(`Attachment ID: ${message.attachment.id}`);
      lines.push(`Attachment type: ${message.attachment.mimeType}`);
      lines.push(`Attachment size: ${message.attachment.size} bytes`);
    }
    lines.push("");
    lines.push(message.text || "(No text)");
    lines.push("");
  }

  return `${lines.join("\n")}\n`;
}

function createZip(files: AsyncZippable): Promise<Uint8Array<ArrayBuffer>> {
  return new Promise((resolve, reject) => {
    zip(files, { level: 6 }, (error, data) => {
      if (error) reject(error);
      else resolve(data);
    });
  });
}
