import { backendBaseUrl } from "./api";

export interface RelayStatus {
  relayConnected: boolean;
  deviceOnline: boolean;
  storageSharingEnabled: boolean;
}

interface PendingRequest {
  resolve: (payload: unknown) => void;
  reject: (error: Error) => void;
  timeout: number;
}

interface PendingDownload {
  chunks: Uint8Array[];
  name: string;
  mimeType: string;
  resolve: (file: { blob: Blob; name: string }) => void;
  reject: (error: Error) => void;
  timeout: number;
}

type StatusListener = (status: RelayStatus) => void;
type ChangeListener = () => void;

export class RelayClient {
  private socket: WebSocket | null = null;
  private reconnectTimer: number | null = null;
  private closed = false;
  private registered = false;
  private status: RelayStatus = {
    relayConnected: false,
    deviceOnline: false,
    storageSharingEnabled: false
  };
  private readonly listeners = new Set<StatusListener>();
  private readonly changeListeners = new Set<ChangeListener>();
  private readonly pending = new Map<string, PendingRequest>();
  private readonly downloads = new Map<string, PendingDownload>();

  constructor(private readonly accessToken: string) {}

  connect(): void {
    this.closed = false;
    if (this.socket && this.socket.readyState <= WebSocket.OPEN) return;
    const socket = new WebSocket(relayUrl());
    this.socket = socket;

    socket.addEventListener("open", () => {
      socket.send(JSON.stringify({ type: "register.viewer", accessToken: this.accessToken }));
    });
    socket.addEventListener("message", (event) => this.handleMessage(String(event.data)));
    socket.addEventListener("close", () => this.handleDisconnect());
    socket.addEventListener("error", () => socket.close());
  }

  close(): void {
    this.closed = true;
    if (this.reconnectTimer !== null) window.clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
    this.socket?.close(1000, "Viewer closed");
    this.socket = null;
    this.rejectPending("Relay connection closed.");
  }

  subscribe(listener: StatusListener): () => void {
    this.listeners.add(listener);
    listener(this.status);
    return () => this.listeners.delete(listener);
  }

  subscribeChanges(listener: ChangeListener): () => void {
    this.changeListeners.add(listener);
    return () => this.changeListeners.delete(listener);
  }

  isDeviceOnline(): boolean {
    return this.registered && this.status.deviceOnline;
  }

  request<T>(type: string, payload: Record<string, unknown> = {}, timeoutMs = 30_000): Promise<T> {
    if (!this.registered || this.socket?.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error("The relay is reconnecting."));
    }
    const requestId = crypto.randomUUID();
    return new Promise<T>((resolve, reject) => {
      const timeout = window.setTimeout(() => {
        this.pending.delete(requestId);
        reject(new Error("The phone did not answer in time."));
      }, timeoutMs);
      this.pending.set(requestId, {
        resolve: resolve as (payload: unknown) => void,
        reject,
        timeout
      });
      this.socket?.send(JSON.stringify({ type, requestId, payload }));
    });
  }

  download(path: string): Promise<{ blob: Blob; name: string }> {
    return this.startDownload("storage.download", { path });
  }

  downloadAttachment(attachmentId: string): Promise<{ blob: Blob; name: string }> {
    return this.startDownload("chat.attachment.download", { attachmentId });
  }

  private startDownload(type: string, payload: Record<string, unknown>): Promise<{ blob: Blob; name: string }> {
    if (!this.registered || !this.status.deviceOnline || this.socket?.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error("The phone is offline."));
    }
    const requestId = crypto.randomUUID();
    return new Promise((resolve, reject) => {
      const timeout = window.setTimeout(() => {
        this.downloads.delete(requestId);
        reject(new Error("The download timed out."));
      }, 10 * 60_000);
      this.downloads.set(requestId, {
        chunks: [],
        name: "download",
        mimeType: "application/octet-stream",
        resolve,
        reject,
        timeout
      });
      this.socket?.send(JSON.stringify({
        type,
        requestId,
        payload
      }));
    });
  }

  private handleMessage(raw: string): void {
    let message: Record<string, unknown>;
    try {
      message = JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return;
    }
    const type = typeof message.type === "string" ? message.type : "";
    if (type === "viewer.registered") {
      this.registered = true;
      this.setStatus({
        relayConnected: true,
        deviceOnline: message.online === true,
        storageSharingEnabled: message.storageSharingEnabled === true
      });
      return;
    }
    if (type === "device.status") {
      this.setStatus({
        relayConnected: true,
        deviceOnline: message.online === true,
        storageSharingEnabled: message.storageSharingEnabled === true
      });
      return;
    }
    if (type === "chat.changed") {
      for (const listener of this.changeListeners) listener();
      return;
    }

    const requestId = typeof message.requestId === "string" ? message.requestId : "";
    if (!requestId) return;
    if (type === "response") {
      const download = this.downloads.get(requestId);
      if (download && message.ok !== true) {
        window.clearTimeout(download.timeout);
        this.downloads.delete(requestId);
        download.reject(new Error(typeof message.error === "string" ? message.error : "Download failed."));
        return;
      }
      const pending = this.pending.get(requestId);
      if (!pending) return;
      window.clearTimeout(pending.timeout);
      this.pending.delete(requestId);
      if (message.ok === true) {
        pending.resolve(message.payload);
      } else {
        pending.reject(new Error(typeof message.error === "string" ? message.error : "Phone request failed."));
      }
      return;
    }
    if (type === "download.start") {
      const download = this.downloads.get(requestId);
      if (!download) return;
      download.name = typeof message.name === "string" ? message.name : "download";
      download.mimeType = typeof message.mimeType === "string" ? message.mimeType : "application/octet-stream";
      return;
    }
    if (type === "download.chunk") {
      const download = this.downloads.get(requestId);
      if (!download || typeof message.data !== "string") return;
      download.chunks.push(decodeBase64(message.data));
      return;
    }
    if (type === "download.complete") {
      const download = this.downloads.get(requestId);
      if (!download) return;
      window.clearTimeout(download.timeout);
      this.downloads.delete(requestId);
      download.resolve({
        blob: new Blob(download.chunks as BlobPart[], { type: download.mimeType }),
        name: download.name
      });
    }
  }

  private handleDisconnect(): void {
    this.socket = null;
    this.registered = false;
    this.setStatus({ relayConnected: false, deviceOnline: false, storageSharingEnabled: false });
    this.rejectPending("Relay connection lost.");
    if (!this.closed) {
      this.reconnectTimer = window.setTimeout(() => this.connect(), 2_000);
    }
  }

  private rejectPending(reason: string): void {
    for (const request of this.pending.values()) {
      window.clearTimeout(request.timeout);
      request.reject(new Error(reason));
    }
    this.pending.clear();
    for (const download of this.downloads.values()) {
      window.clearTimeout(download.timeout);
      download.reject(new Error(reason));
    }
    this.downloads.clear();
  }

  private setStatus(status: RelayStatus): void {
    this.status = status;
    for (const listener of this.listeners) listener(status);
  }
}

function relayUrl(): string {
  const url = new URL(backendBaseUrl);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/relay";
  url.search = "";
  url.hash = "";
  return url.toString();
}

function decodeBase64(value: string): Uint8Array {
  const binary = window.atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}
