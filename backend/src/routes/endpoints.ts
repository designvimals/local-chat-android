import { Router } from "express";
import { timingSafeEqual } from "node:crypto";
import { z } from "zod";
import type { RegisterEndpointRequest } from "@private-chat-storage/api-contracts";
import type { DeviceRegistry } from "../services/deviceRegistry.js";
import type { TokenService } from "../services/tokenService.js";
import { bearerToken, requirePairedDevice, requireViewer } from "./middleware.js";

const registerEndpointSchema = z.object({
  deviceId: z.string().min(1).max(128),
  deviceName: z.string().min(1).max(128),
  endpointUrl: z.string().url(),
  storageSharingEnabled: z.boolean(),
  accessToken: z.string().min(32).max(256),
  pairingAvailable: z.boolean()
});

export function createEndpointsRouter(
  registry: DeviceRegistry,
  tokens: TokenService,
  registrationKey: string
): Router {
  const router = Router();

  router.get("/current", requireViewer(tokens), (_request, response) => {
    response.json(registry.getEndpoint());
  });

  router.post("/register", (request, response) => {
    if (!safeEqual(request.header("x-device-registration-key") ?? "", registrationKey)) {
      response.status(401).json({ error: "Device registration is not authorized." });
      return;
    }
    const pairingCode = bearerToken(request);
    if (!pairingCode || pairingCode.length < 4 || pairingCode.length > 128) {
      response.status(401).json({ error: "Pairing code is required." });
      return;
    }

    const parsed = registerEndpointSchema.safeParse(request.body);
    if (!parsed.success) {
      response.status(400).json({ error: "Endpoint registration is invalid." });
      return;
    }

    tokens.registerPairingCode(pairingCode, parsed.data.accessToken, parsed.data.pairingAvailable);
    const device = registry.registerEndpoint(parsed.data as RegisterEndpointRequest, parsed.data.accessToken);
    console.info(`Registered Android device ${device.deviceId} at ${device.currentEndpoint}`);
    response.status(201).json({
      ok: true,
      deviceId: device.deviceId,
      lastSeen: device.lastSeen
    });
  });

  router.post("/active-session", requirePairedDevice(tokens), (request, response) => {
    const active = z.object({ active: z.boolean() }).safeParse(request.body);
    if (!active.success) {
      response.status(400).json({ error: "Session state is invalid." });
      return;
    }
    registry.setActiveSession(active.data.active);
    response.json({ ok: true });
  });

  return router;
}

function safeEqual(left: string, right: string): boolean {
  const leftBuffer = Buffer.from(left);
  const rightBuffer = Buffer.from(right);
  return leftBuffer.byteLength === rightBuffer.byteLength && timingSafeEqual(leftBuffer, rightBuffer);
}
