import { DurableObject } from "cloudflare:workers";
import {
  isDeviceReply,
  isRecord,
  parseDeviceRegistration,
  parseObject,
  parseViewerCommand,
  parseViewerRegistration,
  safeEqual
} from "./protocol";
import type { AppConfigEnv } from "./appConfig";

export interface RelayEnv extends AppConfigEnv {
  DEVICE_REGISTRATION_KEY: string;
  RELAY_HUB: DurableObjectNamespace<RelayHub>;
  ASSETS: Fetcher;
}

interface UnregisteredSocket {
  role: "unregistered";
  connectionId: string;
}

interface DeviceSocket {
  role: "device";
  connectionId: string;
  deviceId: string;
  deviceName: string;
  pairingCode: string;
  accessToken: string;
  pairingAvailable: boolean;
  storageSharingEnabled: boolean;
}

interface ViewerSocket {
  role: "viewer";
  connectionId: string;
  accessToken: string;
  pendingRequestIds: string[];
}

type SocketAttachment = UnregisteredSocket | DeviceSocket | ViewerSocket;

interface LoginAttempt {
  count: number;
  resetsAt: number;
}

interface LoginPayload {
  pairingCode: string;
  clientType: "web" | "android";
  viewerDeviceId?: string;
  deviceName?: string;
}

const MAX_FRAME_CHARACTERS = 2 * 1024 * 1024;
const MAX_PENDING_REQUESTS = 256;

export class RelayHub extends DurableObject<RelayEnv> {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/relay") return this.acceptWebSocket(request);
    if (url.pathname === "/auth/login" && request.method === "POST") return this.login(request);
    if (url.pathname === "/health") return Response.json({ ...this.stats(), status: "ok" });
    return Response.json({ error: "Not found" }, { status: 404 });
  }

  webSocketMessage(socket: WebSocket, raw: string | ArrayBuffer): void {
    if (typeof raw !== "string" || raw.length > MAX_FRAME_CHARACTERS) {
      socket.close(4400, "Invalid message");
      return;
    }
    const message = parseObject(raw);
    if (!message) {
      socket.close(4400, "Invalid JSON");
      return;
    }

    const identity = this.attachment(socket);
    if (!identity || identity.role === "unregistered") {
      this.register(socket, message);
      return;
    }
    if (identity.role === "viewer") {
      this.forwardViewerCommand(socket, identity, message);
      return;
    }
    if (message.type === "device.update") {
      this.updateDevice(socket, identity, message);
    } else if (message.type === "device.chat.changed") {
      this.broadcast(identity.accessToken, { type: "chat.changed" });
    } else {
      this.forwardDeviceReply(identity, message);
    }
  }

  webSocketClose(socket: WebSocket, code: number, reason: string): void {
    const identity = this.attachment(socket);
    if (identity?.role === "device") {
      const replacement = this.deviceForToken(identity.accessToken, socket);
      if (!replacement) {
        this.broadcast(identity.accessToken, {
          type: "device.status",
          online: false,
          storageSharingEnabled: false
        });
        this.failPending(identity.accessToken, "The phone is offline.");
      }
    }
    try {
      socket.close(code, reason);
    } catch {
      // Cloudflare may already have completed the close handshake.
    }
  }

  webSocketError(socket: WebSocket): void {
    const identity = this.attachment(socket);
    if (identity?.role === "device" && !this.deviceForToken(identity.accessToken, socket)) {
      this.broadcast(identity.accessToken, {
        type: "device.status",
        online: false,
        storageSharingEnabled: false
      });
      this.failPending(identity.accessToken, "The phone is offline.");
    }
  }

  private acceptWebSocket(request: Request): Response {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return Response.json({ error: "WebSocket upgrade required" }, { status: 426 });
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.serializeAttachment({
      role: "unregistered",
      connectionId: crypto.randomUUID()
    } satisfies UnregisteredSocket);
    this.ctx.acceptWebSocket(server);
    return new Response(null, { status: 101, webSocket: client });
  }

  private async login(request: Request): Promise<Response> {
    const clientId = request.headers.get("x-client-ip") || "unknown";
    const attemptKey = `login:${clientId}`;
    const now = Date.now();
    const current = await this.ctx.storage.get<LoginAttempt>(attemptKey);
    if (current && current.resetsAt > now && current.count >= 6) {
      return Response.json({ error: "Too many attempts. Try again in 10 minutes." }, { status: 429 });
    }

    const body = await request.json().catch(() => null);
    const parsed = parseLogin(body);
    if (!parsed) {
      return Response.json({ error: "Enter the six-digit code shown on the phone." }, { status: 400 });
    }

    const candidates = this.sockets("device").filter(({ attachment }) =>
      attachment.pairingAvailable && attachment.pairingCode === parsed.pairingCode
    );
    if (candidates.length !== 1) {
      const active = current && current.resetsAt > now
        ? current
        : { count: 0, resetsAt: now + 10 * 60_000 };
      await this.ctx.storage.put(attemptKey, { ...active, count: active.count + 1 });
      return Response.json({
        error: "No online phone is advertising that code. Create a new code and try again."
      }, { status: 401 });
    }

    const { socket, attachment: device } = candidates[0];
    device.pairingAvailable = false;
    socket.serializeAttachment(device);
    const viewerDeviceId = parsed.clientType === "android"
      ? parsed.viewerDeviceId ?? `phone-viewer-${now}`
      : "viewer-web";
    const viewerName = parsed.clientType === "android"
      ? parsed.deviceName ?? "Android phone"
      : "Web browser";
    this.send(socket, {
      type: "pairing.claimed",
      viewerDeviceId,
      viewerName,
      clientType: parsed.clientType
    });
    await this.ctx.storage.delete(attemptKey);
    return Response.json({
      token: device.accessToken,
      pairedToken: device.accessToken,
      viewerDeviceId,
      friendName: device.deviceName,
      endpointUrl: "connection"
    });
  }

  private register(socket: WebSocket, message: Record<string, unknown>): void {
    if (message.type === "register.device") {
      const registration = parseDeviceRegistration(message);
      if (!registration || !safeEqual(registration.registrationKey, this.env.DEVICE_REGISTRATION_KEY || "")) {
        socket.close(4403, "Device registration rejected");
        return;
      }
      for (const existing of this.sockets("device")) {
        if (existing.socket !== socket && existing.attachment.accessToken === registration.accessToken) {
          existing.socket.close(4409, "Replaced by a new device connection");
        }
      }
      const attachment: DeviceSocket = {
        role: "device",
        connectionId: this.attachment(socket)?.connectionId ?? crypto.randomUUID(),
        deviceId: registration.deviceId,
        deviceName: registration.deviceName,
        pairingCode: registration.pairingCode,
        accessToken: registration.accessToken,
        pairingAvailable: registration.pairingAvailable,
        storageSharingEnabled: registration.storageSharingEnabled
      };
      socket.serializeAttachment(attachment);
      this.send(socket, { type: "device.registered" });
      this.broadcast(attachment.accessToken, {
        type: "device.status",
        online: true,
        storageSharingEnabled: attachment.storageSharingEnabled
      });
      return;
    }

    const registration = parseViewerRegistration(message);
    if (!registration) {
      socket.close(4401, "Registration rejected");
      return;
    }
    const attachment: ViewerSocket = {
      role: "viewer",
      connectionId: this.attachment(socket)?.connectionId ?? crypto.randomUUID(),
      accessToken: registration.accessToken,
      pendingRequestIds: []
    };
    socket.serializeAttachment(attachment);
    const device = this.deviceForToken(attachment.accessToken);
    this.send(socket, {
      type: "viewer.registered",
      online: Boolean(device),
      storageSharingEnabled: device?.attachment.storageSharingEnabled ?? false,
      deviceName: device?.attachment.deviceName ?? null
    });
  }

  private forwardViewerCommand(
    socket: WebSocket,
    viewer: ViewerSocket,
    message: Record<string, unknown>
  ): void {
    const command = parseViewerCommand(message);
    if (!command) {
      this.send(socket, { type: "connection.error", error: "Invalid connection command." });
      return;
    }
    const device = this.deviceForToken(viewer.accessToken);
    if (!device) {
      this.send(socket, {
        type: "response",
        requestId: command.requestId,
        ok: false,
        error: "The phone is offline."
      });
      return;
    }
    if (viewer.pendingRequestIds.includes(command.requestId)) {
      this.send(socket, {
        type: "response",
        requestId: command.requestId,
        ok: false,
        error: "Duplicate request."
      });
      return;
    }
    viewer.pendingRequestIds = [...viewer.pendingRequestIds.slice(-(MAX_PENDING_REQUESTS - 1)), command.requestId];
    socket.serializeAttachment(viewer);
    this.send(device.socket, command);
  }

  private forwardDeviceReply(device: DeviceSocket, message: Record<string, unknown>): void {
    if (!isDeviceReply(message)) return;
    const requestId = String(message.requestId);
    const viewer = this.sockets("viewer").find(({ attachment }) =>
      attachment.accessToken === device.accessToken && attachment.pendingRequestIds.includes(requestId)
    );
    if (!viewer) return;
    this.send(viewer.socket, message);
    if (message.type === "response" || message.type === "download.complete") {
      viewer.attachment.pendingRequestIds = viewer.attachment.pendingRequestIds.filter((id) => id !== requestId);
      viewer.socket.serializeAttachment(viewer.attachment);
    }
  }

  private updateDevice(socket: WebSocket, device: DeviceSocket, message: Record<string, unknown>): void {
    if (typeof message.storageSharingEnabled !== "boolean") return;
    device.storageSharingEnabled = message.storageSharingEnabled;
    socket.serializeAttachment(device);
    this.broadcast(device.accessToken, {
      type: "device.status",
      online: true,
      storageSharingEnabled: device.storageSharingEnabled
    });
  }

  private failPending(accessToken: string, error: string): void {
    for (const viewer of this.sockets("viewer")) {
      if (viewer.attachment.accessToken !== accessToken) continue;
      for (const requestId of viewer.attachment.pendingRequestIds) {
        this.send(viewer.socket, { type: "response", requestId, ok: false, error });
      }
      viewer.attachment.pendingRequestIds = [];
      viewer.socket.serializeAttachment(viewer.attachment);
    }
  }

  private broadcast(accessToken: string, message: Record<string, unknown>): void {
    for (const viewer of this.sockets("viewer")) {
      if (viewer.attachment.accessToken === accessToken) this.send(viewer.socket, message);
    }
  }

  private deviceForToken(accessToken: string, excluding?: WebSocket) {
    return this.sockets("device").find(({ socket, attachment }) =>
      socket !== excluding && attachment.accessToken === accessToken && socket.readyState === WebSocket.OPEN
    );
  }

  private sockets<R extends SocketAttachment["role"]>(role: R): Array<{
    socket: WebSocket;
    attachment: Extract<SocketAttachment, { role: R }>;
  }> {
    const matches: Array<{
      socket: WebSocket;
      attachment: Extract<SocketAttachment, { role: R }>;
    }> = [];
    for (const socket of this.ctx.getWebSockets()) {
      const attachment = this.attachment(socket);
      if (attachment?.role === role) {
        matches.push({
          socket,
          attachment: attachment as Extract<SocketAttachment, { role: R }>
        });
      }
    }
    return matches;
  }

  private attachment(socket: WebSocket): SocketAttachment | null {
    const value: unknown = socket.deserializeAttachment();
    if (!isRecord(value) || typeof value.role !== "string" || typeof value.connectionId !== "string") return null;
    return value as unknown as SocketAttachment;
  }

  private send(socket: WebSocket, message: unknown): void {
    if (socket.readyState !== WebSocket.OPEN) return;
    try {
      socket.send(JSON.stringify(message));
    } catch {
      // A close racing with a forwarded chunk is handled by the close callback.
    }
  }

  private stats() {
    return {
      version: "0.4.0-cloudflare",
      storesFiles: false,
      storesMessages: false,
      devices: this.sockets("device").length,
      viewers: this.sockets("viewer").length
    };
  }
}

function parseLogin(value: unknown): LoginPayload | null {
  if (!isRecord(value) || typeof value.pairingCode !== "string" || !/^\d{6}$/.test(value.pairingCode.trim())) {
    return null;
  }
  const clientType = value.clientType === "android" ? "android" : "web";
  if (value.viewerDeviceId !== undefined && (typeof value.viewerDeviceId !== "string" || value.viewerDeviceId.length > 128)) {
    return null;
  }
  if (value.deviceName !== undefined && (typeof value.deviceName !== "string" || value.deviceName.length > 128)) {
    return null;
  }
  return {
    pairingCode: value.pairingCode.trim(),
    clientType,
    viewerDeviceId: typeof value.viewerDeviceId === "string" ? value.viewerDeviceId.trim() : undefined,
    deviceName: typeof value.deviceName === "string" ? value.deviceName.trim() : undefined
  };
}
