import type { FileItem } from "../types/api";

export function normalizeSearchText(value: string): string {
  return value.normalize("NFC").toLocaleLowerCase().trim();
}

export function fileMatchesSearch(item: FileItem, query: string): boolean {
  const normalizedQuery = normalizeSearchText(query);
  if (!normalizedQuery) return true;
  const normalizedName = normalizeSearchText(item.name);
  return normalizedQuery.split(/\s+/).every((term) => normalizedName.includes(term));
}
