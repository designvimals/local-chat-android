export interface AppConfigEnv {
  PUBLIC_RELAY_URL?: string;
  APP_CONFIG_REVISION?: string;
  APP_LATEST_VERSION?: string;
  APP_MINIMUM_VERSION?: string;
  APP_UPDATE_URL?: string;
  APP_FILE_SHARING_ENABLED?: string;
  APP_MESSAGE_SEARCH_ENABLED?: string;
  APP_CONNECTION_RETRY_MILLIS?: string;
  APP_SYNC_RECOVERY_MILLIS?: string;
  APP_SEND_BOUNCE_SCALE?: string;
  APP_EXPRESSIVE_EFFECTS_ENABLED?: string;
}

export interface PublicAppConfig {
  schemaVersion: 1;
  revision: number;
  relayBaseUrl: string;
  release: {
    latestVersion: string | null;
    minimumVersion: string | null;
    downloadUrl: string | null;
  };
  features: {
    fileSharing: boolean;
    messageSearch: boolean;
  };
  limits: {
    maxAttachmentBytes: number | null;
  };
  timing: {
    connectionRetryMillis: number;
    syncRecoveryMillis: number;
  };
  motion: {
    sentMessageBounceScale: number;
    expressiveEffectsEnabled: boolean;
  };
}

export function buildPublicAppConfig(requestUrl: string, env: AppConfigEnv): PublicAppConfig {
  const origin = new URL(requestUrl).origin;
  return {
    schemaVersion: 1,
    revision: integerInRange(env.APP_CONFIG_REVISION, 1, 1, Number.MAX_SAFE_INTEGER),
    relayBaseUrl: validHttpsOrigin(env.PUBLIC_RELAY_URL) ?? origin,
    release: {
      latestVersion: optionalVersion(env.APP_LATEST_VERSION),
      minimumVersion: optionalVersion(env.APP_MINIMUM_VERSION),
      downloadUrl: validHttpsUrl(env.APP_UPDATE_URL)
    },
    features: {
      fileSharing: booleanValue(env.APP_FILE_SHARING_ENABLED, true),
      messageSearch: booleanValue(env.APP_MESSAGE_SEARCH_ENABLED, true)
    },
    limits: {
      // No application-level attachment limit is currently imposed.
      maxAttachmentBytes: null
    },
    timing: {
      connectionRetryMillis: integerInRange(env.APP_CONNECTION_RETRY_MILLIS, 2_000, 1_000, 60_000),
      syncRecoveryMillis: integerInRange(env.APP_SYNC_RECOVERY_MILLIS, 60_000, 15_000, 15 * 60_000)
    },
    motion: {
      sentMessageBounceScale: numberInRange(env.APP_SEND_BOUNCE_SCALE, 0.7, 0, 1),
      expressiveEffectsEnabled: booleanValue(env.APP_EXPRESSIVE_EFFECTS_ENABLED, true)
    }
  };
}

function validHttpsOrigin(value: string | undefined): string | null {
  const parsed = validHttpsUrl(value);
  if (!parsed) return null;
  const url = new URL(parsed);
  return url.pathname === "/" && !url.search && !url.hash ? url.origin : null;
}

function validHttpsUrl(value: string | undefined): string | null {
  if (!value?.trim()) return null;
  try {
    const url = new URL(value.trim());
    return url.protocol === "https:" ? url.toString().replace(/\/$/, "") : null;
  } catch {
    return null;
  }
}

function optionalVersion(value: string | undefined): string | null {
  const normalized = value?.trim().replace(/^v/i, "") ?? "";
  return /^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(normalized) ? normalized : null;
}

function booleanValue(value: string | undefined, fallback: boolean): boolean {
  if (value === "true") return true;
  if (value === "false") return false;
  return fallback;
}

function integerInRange(
  value: string | undefined,
  fallback: number,
  minimum: number,
  maximum: number
): number {
  const parsed = value == null ? Number.NaN : Number(value);
  return Number.isSafeInteger(parsed) && parsed >= minimum && parsed <= maximum ? parsed : fallback;
}

function numberInRange(
  value: string | undefined,
  fallback: number,
  minimum: number,
  maximum: number
): number {
  const parsed = value == null ? Number.NaN : Number(value);
  return Number.isFinite(parsed) && parsed >= minimum && parsed <= maximum ? parsed : fallback;
}
