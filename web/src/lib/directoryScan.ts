import type { FileItem } from "../types/api";

export interface ReadableFileEntry {
  readonly kind: "file";
  readonly name: string;
}

export interface ReadableDirectoryEntry {
  readonly kind: "directory";
  readonly name: string;
  values(): AsyncIterableIterator<ReadableFileEntry | ReadableDirectoryEntry>;
}

export function normalizeFileName(name: string): string {
  return name.normalize("NFC").toLocaleLowerCase();
}

export function collectSelectedFileNames(
  files: Iterable<Pick<File, "name">>,
  onProgress?: (fileCount: number) => void
): Set<string> {
  const names = new Set<string>();
  let fileCount = 0;

  for (const file of files) {
    names.add(normalizeFileName(file.name));
    fileCount += 1;
    if (fileCount % 250 === 0) onProgress?.(fileCount);
  }

  onProgress?.(fileCount);
  return names;
}

export async function collectDirectoryFileNames(
  directory: ReadableDirectoryEntry,
  onProgress?: (fileCount: number) => void
): Promise<Set<string>> {
  const names = new Set<string>();
  let fileCount = 0;

  async function visit(current: ReadableDirectoryEntry): Promise<void> {
    for await (const entry of current.values()) {
      if (entry.kind === "file") {
        names.add(normalizeFileName(entry.name));
        fileCount += 1;
        if (fileCount % 250 === 0) onProgress?.(fileCount);
      } else {
        await visit(entry);
      }
    }
  }

  await visit(directory);
  onProgress?.(fileCount);
  return names;
}

export function filesMissingByName(files: FileItem[], normalizedExistingNames: ReadonlySet<string>): FileItem[] {
  return files.filter((file) => !normalizedExistingNames.has(normalizeFileName(file.name)));
}
