import { zip, type AsyncZippable } from "fflate";
import { ArrowLeft, Download, Home, ListChecks, RefreshCw, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { RelayClient } from "../lib/relay";
import type { FileItem, StorageListResponse } from "../types/api";
import { FileRow } from "./FileRow";

interface StoragePanelProps {
  relay: RelayClient;
  fullScreen?: boolean;
  onClose: () => void;
}

interface BulkProgress {
  completed: number;
  total: number;
  label: string;
  phase: "downloading" | "packing";
}

type DownloadedFile = Awaited<ReturnType<RelayClient["download"]>>;

export function StoragePanel({ relay, fullScreen = false, onClose }: StoragePanelProps) {
  const [path, setPath] = useState("/");
  const [items, setItems] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [downloadingPath, setDownloadingPath] = useState<string | null>(null);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedPaths, setSelectedPaths] = useState<Set<string>>(() => new Set());
  const [bulkProgress, setBulkProgress] = useState<BulkProgress | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const breadcrumb = useMemo(() => path === "/"
    ? ["Shared folders"]
    : ["Shared folders", ...path.split("/").filter(Boolean)], [path]);
  const files = useMemo(() => items.filter((item) => item.type === "file"), [items]);
  const selectedFiles = useMemo(
    () => files.filter((item) => selectedPaths.has(item.path)),
    [files, selectedPaths]
  );

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await relay.request<StorageListResponse>("storage.list", { path });
      setItems(response.items);
      setSelectedPaths((current) => {
        const visibleFiles = new Set(response.items.filter((item) => item.type === "file").map((item) => item.path));
        return new Set([...current].filter((selectedPath) => visibleFiles.has(selectedPath)));
      });
    } catch (caught) {
      setItems([]);
      setError(caught instanceof Error ? caught.message : "The phone is offline.");
    } finally {
      setLoading(false);
    }
  }, [path, relay]);

  useEffect(() => { void refresh(); }, [refresh]);

  useEffect(() => {
    setSelectionMode(false);
    setSelectedPaths(new Set());
  }, [path]);

  async function handleDownload(item: FileItem) {
    setDownloadingPath(item.path);
    setError(null);
    setNotice(null);
    try {
      const file = await relay.download(item.path);
      saveBlob(file.blob, file.name);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Download failed.");
    } finally {
      setDownloadingPath(null);
    }
  }

  function toggleSelected(item: FileItem) {
    setSelectedPaths((current) => {
      const next = new Set(current);
      if (next.has(item.path)) next.delete(item.path);
      else next.add(item.path);
      return next;
    });
  }

  function toggleAllFiles() {
    setSelectedPaths((current) => current.size === files.length
      ? new Set()
      : new Set(files.map((file) => file.path)));
  }

  function leaveSelectionMode() {
    setSelectionMode(false);
    setSelectedPaths(new Set());
  }

  async function downloadSelectedFiles(onDownloaded: (file: DownloadedFile) => void | Promise<void>) {
    const currentSelection = [...selectedFiles];
    for (const [index, item] of currentSelection.entries()) {
      setBulkProgress({
        completed: index,
        total: currentSelection.length,
        label: item.name,
        phase: "downloading"
      });
      const file = await relay.download(item.path);
      await onDownloaded(file);
    }
    return currentSelection.length;
  }

  async function handleIndividualDownloads() {
    if (selectedFiles.length === 0 || bulkProgress) return;
    setError(null);
    setNotice(null);
    try {
      const downloadedCount = await downloadSelectedFiles((file) => saveBlob(file.blob, file.name));
      setNotice(
        `Started ${downloadedCount} separate downloads. If only one appears, allow multiple downloads in your browser and try again.`
      );
      leaveSelectionMode();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Multiple download failed.");
    } finally {
      setBulkProgress(null);
    }
  }

  async function handleZipDownload() {
    if (selectedFiles.length === 0 || bulkProgress) return;
    setError(null);
    setNotice(null);
    const archive: AsyncZippable = {};
    try {
      const downloadedCount = await downloadSelectedFiles(async (file) => {
        archive[uniqueArchiveName(file.name, archive)] = new Uint8Array(await file.blob.arrayBuffer());
      });
      setBulkProgress({
        completed: downloadedCount,
        total: downloadedCount,
        label: "Creating ZIP…",
        phase: "packing"
      });
      const zipped = await createZip(archive);
      const zipName = archiveName(path);
      saveBlob(new Blob([zipped], { type: "application/zip" }), zipName);
      setNotice(`Downloaded ${downloadedCount} files as ${zipName}.`);
      leaveSelectionMode();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Bulk download failed.");
    } finally {
      setBulkProgress(null);
    }
  }

  function goUp() {
    if (path === "/") {
      onClose();
      return;
    }
    const parts = path.split("/").filter(Boolean);
    parts.pop();
    setPath(parts.length ? `/${parts.join("/")}` : "/");
  }

  return (
    <section className={fullScreen ? "storage-panel fullscreen" : "storage-panel"} aria-label="Friend's storage">
      <header className="storage-header">
        <button type="button" className="icon-button" onClick={goUp} aria-label={path === "/" ? "Back to chat" : "Go up"}>
          <ArrowLeft aria-hidden size={19} />
        </button>
        <div>
          <h2>Friend's Storage</h2>
          <nav aria-label="Current folder" className="breadcrumb">
            {breadcrumb.map((part, index) => <span key={`${part}-${index}`}>{part}</span>)}
          </nav>
        </div>
        <button type="button" className="icon-button" onClick={() => void refresh()} aria-label="Refresh storage">
          <RefreshCw aria-hidden size={18} />
        </button>
      </header>

      <div className="storage-root-action">
        {!selectionMode ? (
          <>
            <button type="button" onClick={() => setPath("/")} className="soft-button">
              <Home aria-hidden size={16} /><span>Shared folders</span>
            </button>
            <span className="storage-toolbar-spacer" />
            {files.length > 0 ? (
              <button type="button" className="soft-button" onClick={() => setSelectionMode(true)}>
                <ListChecks aria-hidden size={16} /><span>Select</span>
              </button>
            ) : null}
          </>
        ) : (
          <div className="selection-toolbar" aria-label="Selected file actions">
            <strong className="selection-count">{selectedFiles.length} selected</strong>
            <button type="button" className="soft-button" onClick={toggleAllFiles} disabled={bulkProgress !== null}>
              {selectedFiles.length === files.length ? "Clear all" : "Select all"}
            </button>
            <button
              type="button"
              className="icon-button compact-icon-button"
              onClick={leaveSelectionMode}
              disabled={bulkProgress !== null}
              aria-label="Cancel file selection"
            >
              <X aria-hidden size={17} />
            </button>
            <div className="bulk-action-group">
              <button
                type="button"
                className="bulk-download-button"
                onClick={() => void handleIndividualDownloads()}
                disabled={selectedFiles.length === 0 || bulkProgress !== null}
              >
                <Download aria-hidden size={16} />
                <span>Download files</span>
              </button>
              <button
                type="button"
                className="bulk-download-button secondary"
                onClick={() => void handleZipDownload()}
                disabled={selectedFiles.length === 0 || bulkProgress !== null}
              >
                <span>Download ZIP</span>
              </button>
            </div>
          </div>
        )}
      </div>

      {bulkProgress ? (
        <div className="bulk-progress" role="status" aria-live="polite">
          <div>
            <strong>{bulkProgress.phase === "packing" ? "Preparing download" : `Downloading ${bulkProgress.completed + 1} of ${bulkProgress.total}`}</strong>
            <span>{bulkProgress.label}</span>
          </div>
          <progress max={bulkProgress.total} value={bulkProgress.completed} />
        </div>
      ) : null}

      {notice ? <div className="bulk-success" role="status" aria-live="polite">{notice}</div> : null}

      {loading ? <div className="storage-state" role="status">Loading storage…</div> : null}
      {!loading && error ? <div className="storage-state warning" role="status">{error}</div> : null}
      {!loading && !error && items.length === 0 ? <div className="storage-state" role="status">This folder is empty.</div> : null}
      {!loading && !error && items.length > 0 ? (
        <ul className="file-list">
          {items.map((item) => (
            <FileRow
              key={item.path}
              item={item}
              busy={bulkProgress !== null}
              downloading={downloadingPath === item.path}
              selected={selectedPaths.has(item.path)}
              selectionMode={selectionMode}
              onOpenFolder={(folder) => setPath(folder.path)}
              onDownload={(file) => void handleDownload(file)}
              onToggleSelected={toggleSelected}
            />
          ))}
        </ul>
      ) : null}
    </section>
  );
}

function saveBlob(blob: Blob, name: string) {
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  anchor.download = name;
  anchor.rel = "noopener";
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

function createZip(files: AsyncZippable): Promise<Uint8Array<ArrayBuffer>> {
  return new Promise((resolve, reject) => {
    zip(files, { level: 0 }, (error, data) => {
      if (error) reject(error);
      else resolve(data);
    });
  });
}

function uniqueArchiveName(name: string, archive: AsyncZippable): string {
  if (!(name in archive)) return name;
  const extensionIndex = name.lastIndexOf(".");
  const base = extensionIndex > 0 ? name.slice(0, extensionIndex) : name;
  const extension = extensionIndex > 0 ? name.slice(extensionIndex) : "";
  let copy = 2;
  while (`${base} (${copy})${extension}` in archive) copy += 1;
  return `${base} (${copy})${extension}`;
}

function archiveName(path: string): string {
  const folder = path.split("/").filter(Boolean).at(-1) ?? "shared-files";
  const safeFolder = folder.replace(/[<>:"/\\|?*\u0000-\u001F]/g, "-").trim() || "shared-files";
  return `${safeFolder}.zip`;
}
