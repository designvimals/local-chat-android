import { RelayHub, type RelayEnv } from "./relayHub";
import { buildPublicAppConfig } from "./appConfig";

export { RelayHub };

export default {
  async fetch(request: Request, env: RelayEnv): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return withCors(new Response(null, { status: 204 }), request);

    if (url.pathname === "/health") {
      return withCors(Response.json({
        status: "ok",
        version: "0.4.0-cloudflare",
        storesFiles: false,
        storesMessages: false
      }), request);
    }

    if (url.pathname === "/app-config") {
      if (request.method !== "GET" && request.method !== "HEAD") {
        return withCors(Response.json({ error: "Method not allowed" }, {
          status: 405,
          headers: { Allow: "GET, HEAD" }
        }), request);
      }
      const config = buildPublicAppConfig(request.url, env);
      const headers = new Headers({
        "Cache-Control": "public, max-age=300, stale-while-revalidate=86400",
        "Content-Type": "application/json; charset=utf-8",
        "ETag": `W/\"app-config-${config.revision}\"`
      });
      const response = request.method === "HEAD"
        ? new Response(null, { headers })
        : new Response(JSON.stringify(config), { headers });
      return withCors(response, request);
    }

    if (url.pathname === "/relay") {
      const hub = env.RELAY_HUB.getByName("global");
      return hub.fetch(request);
    }

    if (url.pathname === "/auth/login" && request.method === "POST") {
      const hub = env.RELAY_HUB.getByName("global");
      const forwarded = new Request("https://between.internal/auth/login", {
        method: "POST",
        headers: {
          "content-type": request.headers.get("content-type") || "application/json",
          "x-client-ip": request.headers.get("CF-Connecting-IP") || "unknown"
        },
        body: request.body
      });
      return withCors(await hub.fetch(forwarded), request);
    }

    if (url.pathname.startsWith("/auth/") || url.pathname.startsWith("/api/")) {
      return withCors(Response.json({ error: "Not found" }, { status: 404 }), request);
    }

    const asset = await env.ASSETS.fetch(request);
    if (asset.status !== 404 || request.method !== "GET" || !request.headers.get("accept")?.includes("text/html")) {
      return asset;
    }
    const fallbackUrl = new URL("/", request.url);
    return env.ASSETS.fetch(new Request(fallbackUrl, request));
  }
};

function withCors(response: Response, request: Request): Response {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", request.headers.get("Origin") || "*");
  headers.set("Access-Control-Allow-Headers", "Content-Type");
  headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  headers.set("Vary", "Origin");
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers
  });
}
