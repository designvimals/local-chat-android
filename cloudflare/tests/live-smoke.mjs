import assert from "node:assert/strict";

const inboxes = new WeakMap();

const baseUrl = process.argv[2];
const registrationKey = process.env.DEVICE_REGISTRATION_KEY;
assert.ok(baseUrl?.startsWith("https://"), "Pass the public HTTPS Worker URL.");
assert.ok(registrationKey?.length >= 16, "DEVICE_REGISTRATION_KEY is required.");

const accessToken = `${crypto.randomUUID()}${crypto.randomUUID()}`;
const pairingCode = String(Math.floor(100000 + Math.random() * 900000));
const relayUrl = new URL("/relay", baseUrl);
relayUrl.protocol = "wss:";

const device = await openSocket(relayUrl);
device.send(JSON.stringify({
  type: "register.device",
  registrationKey,
  deviceId: `smoke-device-${crypto.randomUUID()}`,
  deviceName: "Smoke test phone",
  pairingCode,
  accessToken,
  pairingAvailable: true,
  storageSharingEnabled: false
}));
assert.equal((await nextMessage(device)).type, "device.registered");

const login = await fetch(new URL("/auth/login", baseUrl), {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ pairingCode, clientType: "web" })
});
assert.equal(login.status, 200);
const session = await login.json();
assert.equal(session.pairedToken, accessToken);
assert.equal((await nextMessage(device)).type, "pairing.claimed");

const viewer = await openSocket(relayUrl);
viewer.send(JSON.stringify({ type: "register.viewer", accessToken }));
const registered = await nextMessage(viewer);
assert.equal(registered.type, "viewer.registered");
assert.equal(registered.online, true);

const requestId = crypto.randomUUID();
viewer.send(JSON.stringify({ type: "device.status.get", requestId, payload: {} }));
const command = await nextMessage(device);
assert.equal(command.requestId, requestId);
device.send(JSON.stringify({ type: "response", requestId, ok: true, payload: { online: true } }));
const response = await nextMessage(viewer);
assert.equal(response.requestId, requestId);
assert.equal(response.ok, true);

device.send(JSON.stringify({ type: "device.chat.changed" }));
assert.equal((await nextMessage(viewer)).type, "chat.changed");

viewer.close(1000, "Smoke test complete");
device.close(1000, "Smoke test complete");
console.log("Cloudflare relay smoke test passed.");

function openSocket(url) {
  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    const inbox = { messages: [], waiters: [] };
    inboxes.set(socket, inbox);
    socket.addEventListener("message", (event) => {
      const message = JSON.parse(String(event.data));
      const waiter = inbox.waiters.shift();
      if (waiter) waiter.resolve(message);
      else inbox.messages.push(message);
    });
    socket.addEventListener("close", (event) => {
      for (const waiter of inbox.waiters.splice(0)) {
        waiter.reject(new Error(`WebSocket closed early (${event.code}).`));
      }
    });
    const timeout = setTimeout(() => reject(new Error("WebSocket connection timed out.")), 15_000);
    socket.addEventListener("open", () => {
      clearTimeout(timeout);
      resolve(socket);
    }, { once: true });
    socket.addEventListener("error", () => {
      clearTimeout(timeout);
      reject(new Error("WebSocket connection failed."));
    }, { once: true });
  });
}

function nextMessage(socket) {
  const inbox = inboxes.get(socket);
  assert.ok(inbox, "WebSocket inbox is unavailable.");
  const queued = inbox.messages.shift();
  if (queued) return Promise.resolve(queued);
  return new Promise((resolve, reject) => {
    let waiter;
    const timeout = setTimeout(() => {
      const index = inbox.waiters.indexOf(waiter);
      if (index >= 0) inbox.waiters.splice(index, 1);
      reject(new Error("Timed out waiting for a relay message."));
    }, 15_000);
    waiter = {
      resolve: (message) => {
        clearTimeout(timeout);
        resolve(message);
      },
      reject: (error) => {
        clearTimeout(timeout);
        reject(error);
      }
    };
    inbox.waiters.push(waiter);
  });
}
