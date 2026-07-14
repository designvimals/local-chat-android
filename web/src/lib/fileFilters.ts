import type { FileItem } from "../types/api";

export const fileFilterOptions = [
  { value: "all", label: "All" },
  { value: "images", label: "Images" },
  { value: "videos", label: "Videos" },
  { value: "audio", label: "Audio" },
  { value: "documents", label: "Documents" },
  { value: "archives", label: "Archives" },
  { value: "apps", label: "Apps" },
  { value: "other", label: "Other" }
] as const;

export type FileFilter = typeof fileFilterOptions[number]["value"];
export type FileCategory = Exclude<FileFilter, "all">;

const imageExtensions = new Set([
  "avif", "bmp", "gif", "heic", "heif", "jpeg", "jpg", "png", "svg", "tif", "tiff", "webp"
]);
const videoExtensions = new Set([
  "3gp", "avi", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg", "webm", "wmv"
]);
const audioExtensions = new Set([
  "aac", "amr", "flac", "m4a", "mid", "midi", "mp3", "ogg", "opus", "wav", "wma"
]);
const documentExtensions = new Set([
  "csv", "doc", "docm", "docx", "epub", "htm", "html", "json", "log", "md", "odp", "ods", "odt",
  "pdf", "ppt", "pptm", "pptx", "rtf", "tex", "tsv", "txt", "xls", "xlsm", "xlsx", "xml", "yaml", "yml"
]);
const archiveExtensions = new Set([
  "7z", "bz", "bz2", "cab", "gz", "iso", "lz", "lz4", "rar", "tar", "tgz", "txz", "xz", "zip", "zst"
]);
const appExtensions = new Set(["aab", "apk", "apks", "xapk"]);

export function categorizeFile(item: FileItem): FileCategory {
  const mimeType = item.mimeType?.trim().toLowerCase() ?? "";
  const extension = fileExtension(item.name);

  if (mimeType.startsWith("image/") || imageExtensions.has(extension)) return "images";
  if (mimeType.startsWith("video/") || videoExtensions.has(extension)) return "videos";
  if (mimeType.startsWith("audio/") || audioExtensions.has(extension)) return "audio";
  if (isAppMimeType(mimeType) || appExtensions.has(extension)) return "apps";
  if (isArchiveMimeType(mimeType) || archiveExtensions.has(extension)) return "archives";
  if (isDocumentMimeType(mimeType) || documentExtensions.has(extension)) return "documents";
  return "other";
}

export function fileMatchesFilter(item: FileItem, filter: FileFilter): boolean {
  return item.type === "folder" || filter === "all" || categorizeFile(item) === filter;
}

function fileExtension(name: string): string {
  const separator = name.lastIndexOf(".");
  return separator > -1 ? name.slice(separator + 1).toLowerCase() : "";
}

function isDocumentMimeType(mimeType: string): boolean {
  return mimeType.startsWith("text/") ||
    mimeType === "application/pdf" ||
    mimeType === "application/rtf" ||
    mimeType === "application/epub+zip" ||
    mimeType.includes("officedocument") ||
    mimeType.includes("opendocument") ||
    mimeType.includes("msword") ||
    mimeType.includes("ms-excel") ||
    mimeType.includes("ms-powerpoint");
}

function isArchiveMimeType(mimeType: string): boolean {
  return mimeType === "application/zip" ||
    mimeType === "application/gzip" ||
    mimeType === "application/x-7z-compressed" ||
    mimeType === "application/x-bzip2" ||
    mimeType === "application/x-rar-compressed" ||
    mimeType === "application/vnd.rar" ||
    mimeType === "application/x-tar" ||
    mimeType === "application/x-xz" ||
    mimeType === "application/zstd";
}

function isAppMimeType(mimeType: string): boolean {
  return mimeType === "application/vnd.android.package-archive" ||
    mimeType === "application/x-android-package";
}
