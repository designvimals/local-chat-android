import { Download, File, Folder } from "lucide-react";
import type { FileItem } from "../types/api";

interface FileRowProps {
  item: FileItem;
  busy: boolean;
  downloading: boolean;
  selected: boolean;
  selectionMode: boolean;
  onOpenFolder: (item: FileItem) => void;
  onDownload: (item: FileItem) => void;
  onToggleSelected: (item: FileItem) => void;
}

const sizeFormatter = new Intl.NumberFormat(undefined, {
  maximumFractionDigits: 1
});

export function FileRow({
  item,
  busy,
  downloading,
  selected,
  selectionMode,
  onOpenFolder,
  onDownload,
  onToggleSelected
}: FileRowProps) {
  const isFolder = item.type === "folder";
  const modified = item.lastModified ? new Date(item.lastModified) : null;
  const rowClassName = ["file-row", selectionMode ? "selecting" : "", selected ? "selected" : ""]
    .filter(Boolean)
    .join(" ");

  return (
    <li className={rowClassName}>
      {selectionMode ? (
        <label className="file-select">
          <input
            type="checkbox"
            checked={selected}
            disabled={busy}
            onChange={() => onToggleSelected(item)}
            aria-label={`Select ${item.name}`}
          />
        </label>
      ) : null}
      <button
        type="button"
        className="file-main"
        onClick={() => {
          if (selectionMode) onToggleSelected(item);
          else if (isFolder) onOpenFolder(item);
          else onDownload(item);
        }}
        disabled={busy || downloading}
        aria-label={selectionMode
          ? `${selected ? "Deselect" : "Select"} ${item.name}`
          : isFolder
            ? `Open ${item.name}`
            : `Download ${item.name}`}
      >
        <span className={isFolder ? "file-icon folder" : "file-icon"}>
          {isFolder ? <Folder aria-hidden size={20} /> : <File aria-hidden size={20} />}
        </span>
        <span className="file-text">
          <span className="file-name">{item.name}</span>
          <span className="file-meta">
            {isFolder ? "Folder" : formatSize(item.size)}
            {modified ? ` · ${modified.toLocaleDateString()}` : ""}
          </span>
        </span>
      </button>
      {!isFolder && !selectionMode ? (
        <button
          type="button"
          className="icon-button download-button"
          onClick={() => onDownload(item)}
          disabled={busy || downloading}
          aria-label={`Download ${item.name}`}
        >
          <Download aria-hidden size={18} />
        </button>
      ) : null}
    </li>
  );
}

function formatSize(size: number | null): string {
  if (size === null) {
    return "File";
  }
  if (size < 1024) {
    return `${sizeFormatter.format(size)} B`;
  }
  if (size < 1024 * 1024) {
    return `${sizeFormatter.format(size / 1024)} KB`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${sizeFormatter.format(size / 1024 / 1024)} MB`;
  }
  return `${sizeFormatter.format(size / 1024 / 1024 / 1024)} GB`;
}
