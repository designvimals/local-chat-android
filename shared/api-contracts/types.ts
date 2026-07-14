export type DeviceRole = "viewer" | "storage_owner";
export type MessageStatus = "pending" | "sent" | "delivered" | "read" | "failed";
export type FileItemType = "file" | "folder";
export type SessionStatus = "active" | "closed";

export interface Device {
  deviceId: string;
  deviceName: string;
  role: DeviceRole;
  pairedToken: string;
  lastSeen: string;
  storageSharingEnabled: boolean;
  currentEndpoint: string | null;
  createdAt: string;
}

export interface Message {
  id: string;
  senderDeviceId: string;
  receiverDeviceId: string;
  text: string;
  timestamp: string;
  status: MessageStatus;
  deliveredAt?: string | null;
  readAt?: string | null;
  attachment?: ChatAttachment | null;
  attachments?: ChatAttachment[];
  emphasisLevel?: number;
  reactions?: MessageReaction[];
  replyToMessageId?: string | null;
  editedAt?: string | null;
  deletedAt?: string | null;
  deletedForDeviceIds?: string[];
  updatedAt?: string;
}

export interface MessageReaction {
  emoji: string;
  reactorDeviceIds: string[];
}

export interface ChatAttachment {
  id: string;
  name: string;
  mimeType: string;
  size: number;
  width?: number;
  height?: number;
}

export interface FileItem {
  name: string;
  path: string;
  type: FileItemType;
  size: number | null;
  mimeType: string | null;
  lastModified: string | null;
}

export interface Session {
  sessionId: string;
  viewerDeviceId: string;
  storageOwnerDeviceId: string;
  startedAt: string;
  lastActiveAt: string;
  status: SessionStatus;
}

export interface LoginRequest {
  pairingCode: string;
}

export interface LoginResponse {
  token: string;
  pairedToken: string;
  viewerDeviceId: string;
  friendName: string;
  endpointUrl: string;
}

export interface HealthResponse {
  status: "ok";
  deviceName: string;
  storageSharingEnabled: boolean;
}

export interface MessageListResponse {
  messages: Message[];
}

export interface SendMessageRequest {
  id: string;
  senderDeviceId: string;
  text: string;
  timestamp: string;
}

export interface SendMessageResponse {
  ok: boolean;
  messageId: string;
}

export interface StorageListResponse {
  path: string;
  items: FileItem[];
}

export interface RegisterEndpointRequest {
  deviceId: string;
  deviceName: string;
  endpointUrl: string;
  storageSharingEnabled: boolean;
  accessToken: string;
  pairingAvailable: boolean;
}

export interface ReadReceiptRequest {
  readerDeviceId: string;
  readAt: string;
}

export interface EndpointResponse {
  endpointUrl: string | null;
  storageSharingEnabled: boolean;
  lastSeen: string | null;
}

export interface FriendStatusResponse {
  online: boolean;
  storageSharingEnabled: boolean;
  activeSession: boolean;
  lastSeen: string | null;
}
