import { Router } from "express";
import { z } from "zod";
import type { LoginResponse } from "@private-chat-storage/api-contracts";
import type { RelayHub } from "../services/relayHub.js";

const loginSchema = z.object({
  pairingCode: z.string().trim().regex(/^\d{6}$/),
  clientType: z.enum(["web", "android"]).optional().default("web"),
  viewerDeviceId: z.string().trim().min(1).max(128).optional(),
  deviceName: z.string().trim().min(1).max(128).optional()
});

export function createAuthRouter(relay: RelayHub): Router {
  const router = Router();
  const attempts = new Map<string, { count: number; resetsAt: number }>();

  router.post("/login", (request, response) => {
    const clientId = request.ip || "unknown";
    const now = Date.now();
    const current = attempts.get(clientId);
    if (current && current.resetsAt > now && current.count >= 6) {
      response.status(429).json({ error: "Too many attempts. Try again in 10 minutes." });
      return;
    }

    const parsed = loginSchema.safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ error: "Enter the six-digit code shown on the phone." });
      return;
    }

    const viewerDeviceId = parsed.data.clientType === "android"
      ? parsed.data.viewerDeviceId ?? `phone-viewer-${Date.now()}`
      : "viewer-web";
    const viewerName = parsed.data.clientType === "android"
      ? parsed.data.deviceName ?? "Android phone"
      : "Web browser";
    const claim = relay.claimPairingCode(parsed.data.pairingCode, {
      viewerDeviceId,
      deviceName: viewerName,
      clientType: parsed.data.clientType
    });
    if (!claim) {
      const active = current && current.resetsAt > now ? current : { count: 0, resetsAt: now + 10 * 60_000 };
      attempts.set(clientId, { ...active, count: active.count + 1 });
      response.status(401).json({
        error: "No online phone is advertising that code. Check the phone's relay status or create a new code."
      });
      return;
    }

    attempts.delete(clientId);
    const body: LoginResponse = {
      token: claim.accessToken,
      pairedToken: claim.accessToken,
      viewerDeviceId,
      friendName: claim.deviceName,
      endpointUrl: "relay"
    };
    response.json(body);
  });

  return router;
}
