import { timingSafeEqual } from "node:crypto";
import WebSocket, { type RawData } from "ws";
import { z } from "zod";

const deviceRegistrationSchema = z.object({
  type: z.literal("register.device"),
  registrationKey: z.string().min(16).max(256),
  deviceId: z.string().min(1).max(128),
  deviceName: z.string().min(1).max(128),
  pairingCode: z.string().regex(/^\d{6}$/),
  accessToken: z.string().min(32).max(256),
  pairingAvailable: z.boolean(),
  storageSharingEnabled: z.boolean()
});

const viewerRegistrationSchema = z.object({
  type: z.literal("register.viewer"),
  accessToken: z.string().min(32).max(256)
});

const viewerCommandSchema = z.object({
  type: z.enum([
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
  ]),
  requestId: z.string().min(1).max(128),
  payload: z.record(z.string(), z.unknown()).optional()
});

const deviceReplySchema = z.object({
  type: z.enum([
    "response",
    "download.start",
    "download.chunk",
    "download.complete"
  ]),
  requestId: z.string().min(1).max(128)
}).passthrough();

interface RegisteredDevice {
  socket: WebSocket;
  deviceId: string;
  deviceName: string;
  pairingCode: string;
  accessToken: string;
  pairingAvailable: boolean;
  storageSharingEnabled: boolean;
}

interface PendingRequest {
  viewer: WebSocket;
  deviceAccessToken: string;
  timeout: ReturnType<typeof setTimeout>;
}

export interface PairingClaim {
  accessToken: string;
  deviceName: string;
}

export interface PairingViewer {
  viewerDeviceId: string;
  deviceName: string;
  clientType: "web" | "android";
}

type SocketIdentity =
  | { role: "unregistered" }
  | { role: "device"; accessToken: string }
  | { role: "viewer"; accessToken: string };

/**
 * In-memory transport only. Commands and file chunks are forwarded immediately;
 * nothing is written to a database or retained for offline delivery.
 */
export class RelayHub {
  private readonly devicesByToken = new Map<string, RegisteredDevice>();
  private readonly devicesByCode = new Map<string, RegisteredDevice>();
  private readonly viewersByToken = new Map<string, Set<WebSocket>>();
  private readonly identities = new Map<WebSocket, SocketIdentity>();
  private readonly pending = new Map<string, PendingRequest>();

  constructor(private readonly registrationKey: string) {}

  attach(socket: WebSocket): void {
    this.identities.set(socket, { role: "unregistered" });
    const registrationTimer = setTimeout(() => {
      if (this.identities.get(socket)?.role === "unregistered") {
        socket.close(4401, "Registration required");
      }
    }, 10_000);

    socket.on("message", (data) => this.handleMessage(socket, data));
    socket.on("close", () => {
      clearTimeout(registrationTimer);
      this.detach(socket);
    });
    socket.on("error", () => this.detach(socket));
  }

  claimPairingCode(code: string, viewer: PairingViewer): PairingClaim | null {
    const device = this.devicesByCode.get(code);
    if (!device || device.socket.readyState !== WebSocket.OPEN || !device.pairingAvailable) {
      return null;
    }

    device.pairingAvailable = false;
    this.devicesByCode.delete(code);
    this.send(device.socket, {
      type: "pairing.claimed",
      viewerDeviceId: viewer.viewerDeviceId,
      viewerName: viewer.deviceName,
      clientType: viewer.clientType
    });
    return { accessToken: device.accessToken, deviceName: device.deviceName };
  }

  stats(): { devices: number; viewers: number; pendingRequests: number } {
    return {
      devices: this.devicesByToken.size,
      viewers: [...this.viewersByToken.values()].reduce((total, viewers) => total + viewers.size, 0),
      pendingRequests: this.pending.size
    };
  }

  private handleMessage(socket: WebSocket, data: RawData): void {
    const parsed = parseObject(data);
    if (!parsed) {
      socket.close(4400, "Invalid JSON");
      return;
    }

    const identity = this.identities.get(socket) ?? { role: "unregistered" as const };
    if (identity.role === "unregistered") {
      if (parsed.type === "register.device") {
        this.registerDevice(socket, parsed);
      } else if (parsed.type === "register.viewer") {
        this.registerViewer(socket, parsed);
      } else {
        socket.close(4401, "Register first");
      }
      return;
    }

    if (identity.role === "viewer") {
      this.forwardViewerCommand(socket, identity.accessToken, parsed);
    } else {
      if (parsed.type === "device.update") {
        this.updateDevice(identity.accessToken, parsed);
      } else {
        this.forwardDeviceReply(identity.accessToken, parsed);
      }
    }
  }

  private registerDevice(socket: WebSocket, message: Record<string, unknown>): void {
    const parsed = deviceRegistrationSchema.safeParse(message);
    if (!parsed.success || !safeEqual(parsed.data.registrationKey, this.registrationKey)) {
      socket.close(4403, "Device registration rejected");
      return;
    }

    const previous = this.devicesByToken.get(parsed.data.accessToken);
    if (previous && previous.socket !== socket) {
      previous.socket.close(4409, "Replaced by a new device connection");
      this.detach(previous.socket);
    }

    const device: RegisteredDevice = {
      socket,
      deviceId: parsed.data.deviceId,
      deviceName: parsed.data.deviceName,
      pairingCode: parsed.data.pairingCode,
      accessToken: parsed.data.accessToken,
      pairingAvailable: parsed.data.pairingAvailable,
      storageSharingEnabled: parsed.data.storageSharingEnabled
    };
    this.identities.set(socket, { role: "device", accessToken: device.accessToken });
    this.devicesByToken.set(device.accessToken, device);
    if (device.pairingAvailable) {
      this.devicesByCode.set(device.pairingCode, device);
    }
    this.send(socket, { type: "device.registered" });
    this.broadcastStatus(device.accessToken, true, device.storageSharingEnabled);
    console.info(`Relay device connected: ${device.deviceName} (${device.deviceId})`);
  }

  private registerViewer(socket: WebSocket, message: Record<string, unknown>): void {
    const parsed = viewerRegistrationSchema.safeParse(message);
    if (!parsed.success) {
      socket.close(4401, "Viewer registration rejected");
      return;
    }

    const accessToken = parsed.data.accessToken;
    this.identities.set(socket, { role: "viewer", accessToken });
    const viewers = this.viewersByToken.get(accessToken) ?? new Set<WebSocket>();
    viewers.add(socket);
    this.viewersByToken.set(accessToken, viewers);
    const device = this.devicesByToken.get(accessToken);
    this.send(socket, {
      type: "viewer.registered",
      online: device?.socket.readyState === WebSocket.OPEN,
      storageSharingEnabled: device?.storageSharingEnabled ?? false,
      deviceName: device?.deviceName ?? null
    });
  }

  private forwardViewerCommand(socket: WebSocket, accessToken: string, message: Record<string, unknown>): void {
    const parsed = viewerCommandSchema.safeParse(message);
    if (!parsed.success) {
      this.send(socket, { type: "relay.error", error: "Invalid relay command." });
      return;
    }

    const device = this.devicesByToken.get(accessToken);
    if (!device || device.socket.readyState !== WebSocket.OPEN) {
      this.send(socket, {
        type: "response",
        requestId: parsed.data.requestId,
        ok: false,
        error: "The phone is offline."
      });
      return;
    }

    const existing = this.pending.get(parsed.data.requestId);
    if (existing) {
      clearTimeout(existing.timeout);
      this.pending.delete(parsed.data.requestId);
    }
    const responseTimeoutMs = parsed.data.type === "storage.list" ? 150_000 : 120_000;
    const timeout = setTimeout(() => {
      this.pending.delete(parsed.data.requestId);
      this.send(socket, {
        type: "response",
        requestId: parsed.data.requestId,
        ok: false,
        error: "The phone did not answer in time."
      });
    }, responseTimeoutMs);
    this.pending.set(parsed.data.requestId, { viewer: socket, deviceAccessToken: accessToken, timeout });
    this.send(device.socket, parsed.data);
  }

  private forwardDeviceReply(accessToken: string, message: Record<string, unknown>): void {
    const parsed = deviceReplySchema.safeParse(message);
    if (!parsed.success) return;
    const pending = this.pending.get(parsed.data.requestId);
    if (!pending || pending.deviceAccessToken !== accessToken || pending.viewer.readyState !== WebSocket.OPEN) return;

    this.send(pending.viewer, message);
    if (parsed.data.type === "response" || parsed.data.type === "download.complete") {
      clearTimeout(pending.timeout);
      this.pending.delete(parsed.data.requestId);
    }
  }

  private updateDevice(accessToken: string, message: Record<string, unknown>): void {
    const parsed = z.object({
      type: z.literal("device.update"),
      storageSharingEnabled: z.boolean()
    }).safeParse(message);
    if (!parsed.success) return;
    const device = this.devicesByToken.get(accessToken);
    if (!device) return;
    device.storageSharingEnabled = parsed.data.storageSharingEnabled;
    this.broadcastStatus(accessToken, true, device.storageSharingEnabled);
  }

  private detach(socket: WebSocket): void {
    const identity = this.identities.get(socket);
    this.identities.delete(socket);
    if (!identity || identity.role === "unregistered") return;

    if (identity.role === "device") {
      const device = this.devicesByToken.get(identity.accessToken);
      if (device?.socket === socket) {
        this.devicesByToken.delete(identity.accessToken);
        if (this.devicesByCode.get(device.pairingCode)?.socket === socket) {
          this.devicesByCode.delete(device.pairingCode);
        }
        this.broadcastStatus(identity.accessToken, false, false);
      }
      return;
    }

    const viewers = this.viewersByToken.get(identity.accessToken);
    viewers?.delete(socket);
    if (viewers?.size === 0) this.viewersByToken.delete(identity.accessToken);
    for (const [requestId, request] of this.pending) {
      if (request.viewer === socket) {
        clearTimeout(request.timeout);
        this.pending.delete(requestId);
      }
    }
  }

  private broadcastStatus(accessToken: string, online: boolean, storageSharingEnabled: boolean): void {
    for (const viewer of this.viewersByToken.get(accessToken) ?? []) {
      this.send(viewer, { type: "device.status", online, storageSharingEnabled });
    }
  }

  private send(socket: WebSocket, message: unknown): void {
    if (socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(message));
    }
  }
}

function parseObject(data: RawData): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(data.toString());
    return typeof parsed === "object" && parsed !== null && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null;
  } catch {
    return null;
  }
}

function safeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.byteLength === rightBuffer.byteLength && timingSafeEqual(leftBuffer, rightBuffer);
}
