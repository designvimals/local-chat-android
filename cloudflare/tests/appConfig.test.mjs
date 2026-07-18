import assert from "node:assert/strict";
import test from "node:test";

import { buildPublicAppConfig } from "../src/appConfig.ts";

test("app config uses safe production defaults", () => {
  const config = buildPublicAppConfig("https://relay.example/app-config", {});

  assert.equal(config.schemaVersion, 1);
  assert.equal(config.revision, 1);
  assert.equal(config.relayBaseUrl, "https://relay.example");
  assert.deepEqual(config.release, {
    latestVersion: null,
    minimumVersion: null,
    downloadUrl: null
  });
  assert.equal(config.features.fileSharing, true);
  assert.equal(config.timing.connectionRetryMillis, 2_000);
  assert.equal(config.timing.syncRecoveryMillis, 60_000);
  assert.equal(config.motion.sentMessageBounceScale, 0.7);
});

test("app config accepts valid overrides and rejects unsafe ones", () => {
  const config = buildPublicAppConfig("https://bootstrap.example/app-config", {
    PUBLIC_RELAY_URL: "https://next-relay.example",
    APP_CONFIG_REVISION: "8",
    APP_LATEST_VERSION: "v0.5.0",
    APP_MINIMUM_VERSION: "0.4.5",
    APP_UPDATE_URL: "https://github.com/example/releases/Between-v0.5.0.apk",
    APP_FILE_SHARING_ENABLED: "false",
    APP_CONNECTION_RETRY_MILLIS: "4500",
    APP_SYNC_RECOVERY_MILLIS: "90000",
    APP_SEND_BOUNCE_SCALE: "0.4",
    APP_EXPRESSIVE_EFFECTS_ENABLED: "false"
  });

  assert.equal(config.relayBaseUrl, "https://next-relay.example");
  assert.equal(config.revision, 8);
  assert.equal(config.release.latestVersion, "0.5.0");
  assert.equal(config.release.minimumVersion, "0.4.5");
  assert.equal(config.features.fileSharing, false);
  assert.equal(config.timing.connectionRetryMillis, 4_500);
  assert.equal(config.timing.syncRecoveryMillis, 90_000);
  assert.equal(config.motion.sentMessageBounceScale, 0.4);
  assert.equal(config.motion.expressiveEffectsEnabled, false);

  const unsafe = buildPublicAppConfig("https://bootstrap.example/app-config", {
    PUBLIC_RELAY_URL: "http://insecure.example",
    APP_UPDATE_URL: "javascript:alert(1)",
    APP_CONNECTION_RETRY_MILLIS: "50",
    APP_SEND_BOUNCE_SCALE: "9"
  });
  assert.equal(unsafe.relayBaseUrl, "https://bootstrap.example");
  assert.equal(unsafe.release.downloadUrl, null);
  assert.equal(unsafe.timing.connectionRetryMillis, 2_000);
  assert.equal(unsafe.motion.sentMessageBounceScale, 0.7);
});
