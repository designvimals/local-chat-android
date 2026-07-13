import type {
  Device,
  EndpointResponse,
  FriendStatusResponse,
  RegisterEndpointRequest
} from "@private-chat-storage/api-contracts";

export class DeviceRegistry {
  private storageOwner: Device | null = null;
  private activeSession = false;

  registerEndpoint(request: RegisterEndpointRequest, pairedToken: string): Device {
    const now = new Date().toISOString();
    this.storageOwner = {
      deviceId: request.deviceId,
      deviceName: request.deviceName,
      role: "storage_owner",
      pairedToken,
      lastSeen: now,
      storageSharingEnabled: request.storageSharingEnabled,
      currentEndpoint: request.endpointUrl,
      createdAt: this.storageOwner?.createdAt ?? now
    };
    return this.storageOwner;
  }

  setActiveSession(active: boolean): void {
    this.activeSession = active;
  }

  getEndpoint(): EndpointResponse {
    return {
      endpointUrl: this.storageOwner?.currentEndpoint ?? null,
      storageSharingEnabled: this.storageOwner?.storageSharingEnabled ?? false,
      lastSeen: this.storageOwner?.lastSeen ?? null
    };
  }

  getFriendStatus(): FriendStatusResponse {
    const lastSeen = this.storageOwner?.lastSeen ?? null;
    const online = lastSeen ? Date.now() - Date.parse(lastSeen) < 90_000 : false;
    return {
      online,
      storageSharingEnabled: this.storageOwner?.storageSharingEnabled ?? false,
      activeSession: this.activeSession,
      lastSeen
    };
  }

  getStorageOwner(): Device | null {
    return this.storageOwner;
  }
}
