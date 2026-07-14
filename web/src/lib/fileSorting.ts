import type { FileItem } from "../types/api";

export const fileSortOptions = [
  { value: "name", label: "Name" },
  { value: "date", label: "Date" },
  { value: "size", label: "Size" }
] as const;

export type FileSort = typeof fileSortOptions[number]["value"];
export type FileSortDirection = "ascending" | "descending";

const nameCollator = new Intl.Collator(undefined, { numeric: true, sensitivity: "base" });

export function defaultSortDirection(sort: FileSort): FileSortDirection {
  return sort === "name" ? "ascending" : "descending";
}

export function sortDirectionLabel(sort: FileSort, direction: FileSortDirection): string {
  if (sort === "name") return direction === "ascending" ? "A–Z" : "Z–A";
  if (sort === "date") return direction === "ascending" ? "Oldest" : "Newest";
  return direction === "ascending" ? "Smallest" : "Largest";
}

export function sortFileItems(
  items: readonly FileItem[],
  sort: FileSort,
  direction: FileSortDirection
): FileItem[] {
  return [...items].sort((first, second) => {
    if (first.type !== second.type) return first.type === "folder" ? -1 : 1;

    const comparison = compareBy(first, second, sort, direction);
    return comparison || nameCollator.compare(first.name, second.name);
  });
}

function compareBy(
  first: FileItem,
  second: FileItem,
  sort: FileSort,
  direction: FileSortDirection
): number {
  if (sort === "name") {
    return applyDirection(nameCollator.compare(first.name, second.name), direction);
  }
  if (sort === "date") {
    return compareNullableNumbers(toTimestamp(first.lastModified), toTimestamp(second.lastModified), direction);
  }
  return compareNullableNumbers(first.size, second.size, direction);
}

function compareNullableNumbers(
  first: number | null,
  second: number | null,
  direction: FileSortDirection
): number {
  if (first === null && second === null) return 0;
  if (first === null) return 1;
  if (second === null) return -1;
  return applyDirection(first - second, direction);
}

function applyDirection(comparison: number, direction: FileSortDirection): number {
  return direction === "ascending" ? comparison : -comparison;
}

function toTimestamp(value: string | null): number | null {
  if (!value) return null;
  const timestamp = Date.parse(value);
  return Number.isFinite(timestamp) ? timestamp : null;
}
