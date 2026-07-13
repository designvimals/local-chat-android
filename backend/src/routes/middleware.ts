import type { NextFunction, Request, Response } from "express";
import type { TokenService } from "../services/tokenService.js";

export function bearerToken(request: Request): string | undefined {
  const header = request.header("authorization");
  if (!header?.startsWith("Bearer ")) {
    return undefined;
  }
  return header.slice("Bearer ".length).trim();
}

export function requireViewer(tokens: TokenService) {
  return (request: Request, response: Response, next: NextFunction) => {
    const session = tokens.getSession(bearerToken(request));
    if (!session) {
      response.status(401).json({ error: "Sign in again to continue." });
      return;
    }
    response.locals.session = session;
    next();
  };
}

export function requirePairedDevice(tokens: TokenService) {
  return (request: Request, response: Response, next: NextFunction) => {
    if (!tokens.isPairedToken(bearerToken(request))) {
      response.status(401).json({ error: "Pairing token is required." });
      return;
    }
    next();
  };
}
