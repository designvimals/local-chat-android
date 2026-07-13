import cors from "cors";
import express from "express";
import helmet from "helmet";
import { existsSync } from "node:fs";
import { createServer } from "node:http";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { WebSocketServer } from "ws";
import { db } from "./db/client.js";
import { createAuthRouter } from "./routes/auth.js";
import { RelayHub } from "./services/relayHub.js";

const port = Number(process.env.PORT ?? 8787);
const registrationKey = process.env.DEVICE_REGISTRATION_KEY ?? "local-dev-registration-key-change-me";
const relay = new RelayHub(registrationKey);
const app = express();

app.use(helmet());
app.use(cors({ origin: true }));
app.use(express.json({ limit: "1mb" }));

app.get("/health", (_request, response) => {
  response.json({
    status: "ok",
    version: "0.3.0",
    startedAt: db.startedAt,
    storesFiles: false,
    storesMessages: false,
    relay: relay.stats()
  });
});

app.use("/auth", createAuthRouter(relay));

const webDist = resolve(dirname(fileURLToPath(import.meta.url)), "../../web/dist");
if (existsSync(webDist)) {
  app.use(express.static(webDist));
  app.use((request, response, next) => {
    if (request.method === "GET" && request.accepts("html")) {
      response.sendFile(resolve(webDist, "index.html"));
      return;
    }
    next();
  });
}

app.use((_request, response) => response.status(404).json({ error: "Not found" }));

const server = createServer(app);
const webSockets = new WebSocketServer({ noServer: true, maxPayload: 2 * 1024 * 1024 });

server.on("upgrade", (request, socket, head) => {
  const url = new URL(request.url ?? "/", `http://${request.headers.host ?? "localhost"}`);
  if (url.pathname !== "/relay") {
    socket.destroy();
    return;
  }
  webSockets.handleUpgrade(request, socket, head, (webSocket) => relay.attach(webSocket));
});

server.listen(port, () => {
  console.log(`Between zero-retention relay listening on http://localhost:${port}`);
});
