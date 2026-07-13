export const schemaDescription = {
  devices: "In-memory POC device registry. Replace with SQLite/Postgres metadata tables when needed.",
  messages: "No message table. Chat syncs directly between paired devices.",
  sessions: "In-memory viewer sessions keyed by opaque token."
} as const;
