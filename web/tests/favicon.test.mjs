import assert from "node:assert/strict";
import test from "node:test";
import { buildFaviconDataUri } from "../src/lib/favicon.ts";

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
