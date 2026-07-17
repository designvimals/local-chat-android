export const PAIRING_CLIENT_TYPES = ["web", "android"] as const;

export type PairingClientType = typeof PAIRING_CLIENT_TYPES[number];

export function normalizeClaimedClientTypes(
  pairedClientTypes: readonly PairingClientType[] | undefined,
  legacyPairingAvailable: boolean
): Set<PairingClientType> {
  if (pairedClientTypes) {
    return new Set(pairedClientTypes);
  }

  return legacyPairingAvailable
    ? new Set<PairingClientType>()
    : new Set(PAIRING_CLIENT_TYPES);
}

export function canClaimPairingSlot(
  claimedClientTypes: ReadonlySet<PairingClientType>,
  clientType: PairingClientType
): boolean {
  return !claimedClientTypes.has(clientType);
}

export function hasOpenPairingSlot(claimedClientTypes: ReadonlySet<PairingClientType>): boolean {
  return PAIRING_CLIENT_TYPES.some((clientType) => !claimedClientTypes.has(clientType));
}
