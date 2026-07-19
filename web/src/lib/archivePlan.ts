import type { FileItem } from "../types/api";

export interface ArchiveFileItem extends FileItem {
  type: "file";
  archivePath: string;
}

interface ArchiveCollectionProgress {
  foldersScanned: number;
  filesFound: number;
  currentPath: string;
}

interface ArchiveCollectionOptions {
  signal?: AbortSignal;
  onProgress?: (progress: ArchiveCollectionProgress) => void;
}

export const ARCHIVE_PART_FILE_LIMIT = 5;
export const ARCHIVE_PART_BYTE_LIMIT = 64 * 1024 * 1024;

export async function collectSelectedArchiveFiles(
  roots: FileItem[],
  basePath: string,
  listFolder: (path: string) => Promise<FileItem[]>,
  options: ArchiveCollectionOptions = {}
): Promise<ArchiveFileItem[]> {
  const pending = [...roots].sort(comparePaths).reverse();
  const visitedFolders = new Set<string>();
  const filesByPath = new Map<string, ArchiveFileItem>();
  let foldersScanned = 0;

  while (pending.length > 0) {
    throwIfAborted(options.signal);
    const item = pending.pop();
    if (!item) break;

    if (item.type === "file") {
      if (!filesByPath.has(item.path)) {
        filesByPath.set(item.path, toArchiveFile(item, basePath));
      }
      continue;
    }

    if (visitedFolders.has(item.path)) continue;
    visitedFolders.add(item.path);
    const children = await listFolder(item.path);
    throwIfAborted(options.signal);
    foldersScanned += 1;
    options.onProgress?.({
      foldersScanned,
      filesFound: filesByPath.size + children.filter((child) => child.type === "file").length,
      currentPath: item.path
    });
    pending.push(...children.sort(comparePaths).reverse());
  }

  return [...filesByPath.values()].sort((left, right) => (
    left.archivePath.localeCompare(right.archivePath, undefined, { numeric: true, sensitivity: "base" })
  ));
}

export function toArchiveFile(item: FileItem, basePath: string): ArchiveFileItem {
  if (item.type !== "file") throw new Error("Only files can be added to an archive.");
  return {
    ...item,
    type: "file",
    archivePath: relativeArchivePath(item.path, basePath, item.name)
  };
}

export function relativeArchivePath(itemPath: string, basePath: string, fallbackName: string): string {
  const itemParts = safePathParts(itemPath);
  const baseParts = safePathParts(basePath);
  const sharesBase = baseParts.every((part, index) => itemParts[index] === part);
  const relativeParts = sharesBase ? itemParts.slice(baseParts.length) : [];
  return (relativeParts.length > 0 ? relativeParts : [safePathPart(fallbackName)])
    .filter(Boolean)
    .join("/") || "download";
}

export function splitArchiveParts(
  items: ArchiveFileItem[],
  fileLimit = ARCHIVE_PART_FILE_LIMIT,
  byteLimit = ARCHIVE_PART_BYTE_LIMIT
): ArchiveFileItem[][] {
  const parts: ArchiveFileItem[][] = [];
  let current: ArchiveFileItem[] = [];
  let currentBytes = 0;
  for (const item of items) {
    const size = item.size ?? 0;
    if (current.length > 0 && (current.length >= fileLimit || currentBytes + size > byteLimit)) {
      parts.push(current);
      current = [];
      currentBytes = 0;
    }
    current.push(item);
    currentBytes += size;
  }
  if (current.length > 0) parts.push(current);
  return parts;
}

export function inferCompletedArchiveParts(parts: ArchiveFileItem[][], nextIndex: number): number {
  let completed = 0;
  let partEnd = 0;
  for (const part of parts) {
    partEnd += part.length;
    if (partEnd > nextIndex) break;
    completed += 1;
  }
  return completed;
}

export function archivePartBounds(parts: ArchiveFileItem[][], partIndex: number): { start: number; end: number } {
  const start = parts.slice(0, partIndex).reduce((total, part) => total + part.length, 0);
  return { start, end: start + (parts[partIndex]?.length ?? 0) };
}

function comparePaths(left: FileItem, right: FileItem): number {
  return left.path.localeCompare(right.path, undefined, { numeric: true, sensitivity: "base" });
}

function safePathParts(path: string): string[] {
  return path.replaceAll("\\", "/").split("/")
    .map(safePathPart)
    .filter(Boolean);
}

function safePathPart(part: string): string {
  const cleaned = part.trim();
  return cleaned === "." || cleaned === ".." ? "" : cleaned;
}

function throwIfAborted(signal: AbortSignal | undefined): void {
  if (!signal?.aborted) return;
  const error = new Error("Download cancelled.");
  error.name = "AbortError";
  throw error;
}
