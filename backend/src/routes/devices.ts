import { Router } from "express";
import type { DeviceRegistry } from "../services/deviceRegistry.js";
import type { TokenService } from "../services/tokenService.js";
import { requireViewer } from "./middleware.js";

export function createDevicesRouter(registry: DeviceRegistry, tokens: TokenService): Router {
  const router = Router();

  router.get("/friend/status", requireViewer(tokens), (_request, response) => {
    response.json(registry.getFriendStatus());
  });

  return router;
}
