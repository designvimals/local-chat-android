import assert from "node:assert/strict";
import test from "node:test";
import {
  canClaimPairingSlot,
  hasOpenPairingSlot,
  normalizeClaimedClientTypes,
  type PairingClientType
} from "../src/services/pairingSlots.js";

function claimInOrder(order: PairingClientType[]): Set<PairingClientType> {
  const claimed = normalizeClaimedClientTypes([], true);
  for (const clientType of order) {
    assert.equal(canClaimPairingSlot(claimed, clientType), true);
    claimed.add(clientType);
  }
  return claimed;
}

test("one code accepts web then Android and closes", () => {
  const claimed = claimInOrder(["web", "android"]);
  assert.equal(hasOpenPairingSlot(claimed), false);
});

test("one code accepts Android then web and closes", () => {
  const claimed = claimInOrder(["android", "web"]);
  assert.equal(hasOpenPairingSlot(claimed), false);
});

test("a client type cannot claim the same code twice", () => {
  const claimed = claimInOrder(["web"]);
  assert.equal(canClaimPairingSlot(claimed, "web"), false);
  assert.equal(canClaimPairingSlot(claimed, "android"), true);
  assert.equal(hasOpenPairingSlot(claimed), true);
});

test("an unclaimed legacy registration exposes both slots", () => {
  const claimed = normalizeClaimedClientTypes(undefined, true);
  assert.deepEqual([...claimed], []);
  assert.equal(hasOpenPairingSlot(claimed), true);
});

test("a claimed legacy registration stays closed", () => {
  const claimed = normalizeClaimedClientTypes(undefined, false);
  assert.deepEqual([...claimed].sort(), ["android", "web"]);
  assert.equal(hasOpenPairingSlot(claimed), false);
});
