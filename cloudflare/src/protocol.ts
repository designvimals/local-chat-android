export const VIEWER_COMMANDS = new Set([
  "chat.sync",
  "chat.send",
  "chat.read",
  "chat.typing",
  "chat.attachment.upload.start",
  "chat.attachment.upload.chunk",
  "chat.attachment.upload.complete",
  "chat.attachment.download",
  "storage.list",
  "storage.download",
  "device.status.get"
]);

export const DEVICE_REPLIES = new Set([
  "response",
  "download.start",
  "download.chunk",
  "download.complete"
]);

export interface DeviceRegistration {
  type: "register.device";
  registrationKey: string;
  deviceId: string;
  deviceName: string;
  pairingCode: string;
  accessToken: string;
  pairingAvailable: boolean;
  storageSharingEnabled: boolean;
}

export interface ViewerRegistration {
  type: "register.viewer";
  accessToken: string;
}

export interface ViewerCommand {
  type: string;
  requestId: string;
  payload?: Record<string, unknown>;
}

export function parseObject(raw: string): Record<string, unknown> | null {
  try {
    const value: unknown = JSON.parse(raw);
    return isRecord(value) ? value : null;
  } catch {
    return null;
  }
}

export function parseDeviceRegistration(value: Record<string, unknown>): DeviceRegistration | null {
  if (
    value.type !== "register.device" ||
    !boundedString(value.registrationKey, 16, 256) ||
    !boundedString(value.deviceId, 1, 128) ||
    !boundedString(value.deviceName, 1, 128) ||
    typeof value.pairingCode !== "string" ||
    !/^\d{6}$/.test(value.pairingCode) ||
    !boundedString(value.accessToken, 32, 256) ||
    typeof value.pairingAvailable !== "boolean" ||
    typeof value.storageSharingEnabled !== "boolean"
  ) {
    return null;
  }
  return value as unknown as DeviceRegistration;
}

export function parseViewerRegistration(value: Record<string, unknown>): ViewerRegistration | null {
  if (value.type !== "register.viewer" || !boundedString(value.accessToken, 32, 256)) return null;
  return value as unknown as ViewerRegistration;
}

export function parseViewerCommand(value: Record<string, unknown>): ViewerCommand | null {
  if (
    typeof value.type !== "string" ||
    !VIEWER_COMMANDS.has(value.type) ||
    !boundedString(value.requestId, 1, 128) ||
    (value.payload !== undefined && !isRecord(value.payload))
  ) {
    return null;
  }
  return value as unknown as ViewerCommand;
}

export function isDeviceReply(value: Record<string, unknown>): boolean {
  return typeof value.type === "string" &&
    DEVICE_REPLIES.has(value.type) &&
    boundedString(value.requestId, 1, 128);
}

export function safeEqual(left: string, right: string): boolean {
  const maximum = Math.max(left.length, right.length);
  let difference = left.length ^ right.length;
  for (let index = 0; index < maximum; index += 1) {
    difference |= (left.charCodeAt(index) || 0) ^ (right.charCodeAt(index) || 0);
  }
  return difference === 0;
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function boundedString(value: unknown, minimum: number, maximum: number): value is string {
  return typeof value === "string" && value.length >= minimum && value.length <= maximum;
}
