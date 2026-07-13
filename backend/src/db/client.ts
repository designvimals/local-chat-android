export interface PseudoDb {
  startedAt: string;
}

export const db: PseudoDb = {
  startedAt: new Date().toISOString()
};
