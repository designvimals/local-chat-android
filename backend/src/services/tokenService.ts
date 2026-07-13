import { randomUUID, timingSafeEqual } from "node:crypto";
import type { DeviceRole } from "@private-chat-storage/api-contracts";

export interface SessionRecord {
  token: string;
  role: DeviceRole;
  deviceId: string;
  pairedToken: string;
  createdAt: string;
}

export class TokenService {
  private readonly sessions = new Map<string, SessionRecord>();
  private activePairingCode: string;
  private activeAccessToken: string;
  private pairingCodeClaimed = false;

  constructor(initialPairedToken: string) {
    this.activePairingCode = initialPairedToken;
    this.activeAccessToken = initialPairedToken;
  }

  loginWithPairingCode(pairingCode: string): SessionRecord | null {
    if (this.pairingCodeClaimed || !safeEqual(pairingCode, this.activePairingCode)) {
      return null;
    }

    this.pairingCodeClaimed = true;
    const token = `viewer_${randomUUID()}`;
    const session: SessionRecord = {
      token,
      role: "viewer",
      deviceId: "viewer-web",
      pairedToken: this.activeAccessToken,
      createdAt: new Date().toISOString()
    };
    this.sessions.set(token, session);
    return session;
  }

  getPairedToken(): string {
    return this.activeAccessToken;
  }

  registerPairingCode(pairingCode: string, accessToken: string, pairingAvailable: boolean): void {
    const codeChanged = !safeEqual(pairingCode, this.activePairingCode);
    this.activePairingCode = pairingCode;
    this.activeAccessToken = accessToken;
    if (codeChanged) {
      this.pairingCodeClaimed = !pairingAvailable;
    }
  }

  getSession(token: string | undefined): SessionRecord | null {
    if (!token) {
      return null;
    }
    return this.sessions.get(token) ?? null;
  }

  isPairedToken(token: string | undefined): boolean {
    if (!token) {
      return false;
    }
    return safeEqual(token, this.activeAccessToken);
  }

  isPairingCodeClaimed(): boolean {
    return this.pairingCodeClaimed;
  }
}

function safeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  if (leftBuffer.byteLength !== rightBuffer.byteLength) {
    return false;
  }
  return timingSafeEqual(leftBuffer, rightBuffer);
}
