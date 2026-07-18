import assert from "node:assert/strict";
import test from "node:test";
import {
  isDeviceReply,
  parseDeviceRegistration,
  parseObject,
  parseViewerCommand,
  safeEqual
} from "../src/protocol.ts";

test("device registration requires the complete private contract", () => {
  const valid = {
    type: "register.device",
    registrationKey: "1234567890123456",
    deviceId: "phone-a",
    deviceName: "Phone A",
    pairingCode: "123456",
    accessToken: "a".repeat(32),
    pairingAvailable: true,
    storageSharingEnabled: false
  };
  assert.deepEqual(parseDeviceRegistration(valid), valid);
  assert.equal(parseDeviceRegistration({ ...valid, pairingCode: "12345" }), null);
  assert.equal(parseDeviceRegistration({ ...valid, accessToken: "short" }), null);
});

test("only allowlisted viewer commands are forwarded", () => {
  assert.ok(parseViewerCommand({ type: "chat.sync", requestId: "one", payload: {} }));
  assert.equal(parseViewerCommand({ type: "admin.reset", requestId: "one" }), null);
  assert.equal(parseViewerCommand({ type: "chat.sync", requestId: "" }), null);
});

test("device replies require a routable request id", () => {
  assert.equal(isDeviceReply({ type: "download.chunk", requestId: "one", data: "abc" }), true);
  assert.equal(isDeviceReply({ type: "device.status", requestId: "one" }), false);
});

test("JSON parsing and secret comparison reject malformed values", () => {
  assert.deepEqual(parseObject('{"ok":true}'), { ok: true });
  assert.equal(parseObject("[]"), null);
  assert.equal(parseObject("{"), null);
  assert.equal(safeEqual("same-secret", "same-secret"), true);
  assert.equal(safeEqual("same-secret", "different-secret"), false);
  assert.equal(safeEqual("short", "shorter"), false);
});
