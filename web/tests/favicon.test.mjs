import assert from "node:assert/strict";
import test from "node:test";
import { appTitleForStatus, buildFaviconDataUri } from "../src/lib/favicon.ts";

test("tab title names the current connection state", () => {
  assert.equal(appTitleForStatus("online"), "Between · Online");
  assert.equal(appTitleForStatus("connecting"), "Between · Reconnecting");
  assert.equal(appTitleForStatus("offline"), "Between · Offline");
});

test("favicon data URI carries the online status color", () => {
  const favicon = buildFaviconDataUri("online");
  assert.match(favicon, /^data:image\/svg\+xml,/);
  assert.match(decodeURIComponent(favicon), /#2d9b63/);
});

test("favicon data URI differentiates offline and reconnecting states", () => {
  const offline = decodeURIComponent(buildFaviconDataUri("offline"));
  const connecting = decodeURIComponent(buildFaviconDataUri("connecting"));
  assert.match(offline, /#87928d/);
  assert.match(connecting, /#d49a3a/);
  assert.notEqual(offline, connecting);
});
