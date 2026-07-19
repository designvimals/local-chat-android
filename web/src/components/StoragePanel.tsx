import { zip, type AsyncZippable } from "fflate";
import { Archive, ArrowDown, ArrowLeft, ArrowUp, ArrowUpDown, Download, FolderSearch, Home, ListChecks, ListFilter, RefreshCw, RotateCcw, Search, Trash2, WifiOff, X } from "lucide-react";
import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import {
  clearDownloadBatch,
  createDownloadBatch,
  loadDownloadBatch,
  saveDownloadBatch,
  saveDownloadProgress,
  type DownloadBatch
} from "../lib/downloadQueue";
import {
  archivePartBounds,
  collectSelectedArchiveFiles,
  inferCompletedArchiveParts,
  splitArchiveParts
} from "../lib/archivePlan";
import {
  clearStagedArchiveFiles,
  deleteStagedArchiveFiles,
  loadStagedArchiveFile,
  requestPersistentArchiveStorage,
  saveStagedArchiveFile
} from "../lib/archiveStaging";
import {
  collectDirectoryFileNames,
  collectSelectedFileNames,
  filesMissingByName,
  type ReadableDirectoryEntry
} from "../lib/directoryScan";
import { categorizeFile, fileFilterOptions, fileMatchesFilter, type FileFilter } from "../lib/fileFilters";
import { fileMatchesSearch } from "../lib/fileSearch";
import {
  defaultSortDirection,
  fileSortOptions,
  sortDirectionLabel,
  sortFileItems,
  type FileSort,
  type FileSortDirection
} from "../lib/fileSorting";
import type { RelayClient } from "../lib/relay";
import type { FileItem, StorageListResponse } from "../types/api";
import { FileRow } from "./FileRow";

interface StoragePanelProps {
  relay: RelayClient;
  queueOwnerId: string;
  fullScreen?: boolean;
  onClose: () => void;
}

interface BulkProgress {
  completed: number;
  total: number;
  label: string;
  phase: "downloading" | "packing" | "scanning";
}

type DirectoryPickerWindow = Window & {
  showDirectoryPicker?: (options?: {
    id?: string;
    mode?: "read";
    startIn?: "downloads";
  }) => Promise<ReadableDirectoryEntry>;
};

interface SelectedDirectoryFiles {
  directoryName: string;
  files: File[];
}

interface TransferMeter {
  startedAt: number;
  confirmedBytes: number;
  lastUiUpdateAt: number;
  samples: Array<{ time: number; bytes: number }>;
}

function selectDirectoryFiles(): Promise<SelectedDirectoryFiles> {
  return new Promise((resolve, reject) => {
    const input = document.createElement("input");
    input.type = "file";
    input.multiple = true;
    input.setAttribute("webkitdirectory", "");
    input.setAttribute("directory", "");
    input.style.display = "none";

    let settled = false;
    const finish = (callback: () => void) => {
      if (settled) return;
      settled = true;
      input.remove();
      callback();
    };

    input.addEventListener("change", () => {
      const files = Array.from(input.files ?? []);
      if (files.length === 0) {
        finish(() => reject(new DOMException("Folder selection cancelled.", "AbortError")));
        return;
      }

      const firstRelativePath = files[0]?.webkitRelativePath ?? "";
      const directoryName = firstRelativePath.split("/")[0] || "selected folder";
      finish(() => resolve({ directoryName, files }));
    }, { once: true });

    input.addEventListener("cancel", () => {
      finish(() => reject(new DOMException("Folder selection cancelled.", "AbortError")));
    }, { once: true });

    document.body.append(input);
    input.click();
  });
}

const RECEIVER_BATCH_SIZE = 10;
const INTER_FILE_PAUSE_MS = 40;
const RECEIVER_BATCH_PAUSE_MS = 250;
const TRANSFER_SAMPLE_WINDOW_MS = 4_000;
const TRANSFER_UI_INTERVAL_MS = 500;
const STORAGE_LIST_TIMEOUT_MS = 2 * 60_000;
const transferNumberFormatter = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });

export function StoragePanel({ relay, queueOwnerId, fullScreen = false, onClose }: StoragePanelProps) {
  const [path, setPath] = useState("/");
  const [items, setItems] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [downloadingPath, setDownloadingPath] = useState<string | null>(null);
  const [fileFilter, setFileFilter] = useState<FileFilter>("all");
  const [fileSort, setFileSort] = useState<FileSort>("name");
  const [sortDirection, setSortDirection] = useState<FileSortDirection>("ascending");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedPaths, setSelectedPaths] = useState<Set<string>>(() => new Set());
  const [bulkProgress, setBulkProgress] = useState<BulkProgress | null>(null);
  const [pendingBatch, setPendingBatch] = useState<DownloadBatch | null>(null);
  const [downloadQueueReady, setDownloadQueueReady] = useState(false);
  const [transferSpeedBps, setTransferSpeedBps] = useState<number | null>(null);
  const [deviceOnline, setDeviceOnline] = useState(() => relay.isDeviceOnline());
  const [storageAvailable, setStorageAvailable] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const batchRunActiveRef = useRef(false);
  const batchAbortControllerRef = useRef<AbortController | null>(null);
  const autoResumeAttemptedRef = useRef(false);
  const transferMeterRef = useRef<TransferMeter | null>(null);
  const deferredSearchQuery = useDeferredValue(searchQuery);

  const breadcrumb = useMemo(() => path === "/"
    ? ["Shared folders"]
    : ["Shared folders", ...path.split("/").filter(Boolean)], [path]);
  const allFiles = useMemo(() => items.filter((item) => item.type === "file"), [items]);
  const filteredItems = useMemo(
    () => items.filter((item) => fileMatchesFilter(item, fileFilter) && fileMatchesSearch(item, deferredSearchQuery)),
    [deferredSearchQuery, fileFilter, items]
  );
  const hasSearchQuery = deferredSearchQuery.trim().length > 0;
  const sortedItems = useMemo(
    () => sortFileItems(filteredItems, fileSort, sortDirection),
    [fileSort, filteredItems, sortDirection]
  );
  const files = useMemo(
    () => sortedItems.filter((item) => item.type === "file"),
    [sortedItems]
  );
  const filterCounts = useMemo(() => {
    const counts: Record<FileFilter, number> = {
      all: allFiles.length,
      images: 0,
      videos: 0,
      audio: 0,
      documents: 0,
      archives: 0,
      apps: 0,
      other: 0
    };
    for (const file of allFiles) counts[categorizeFile(file)] += 1;
    return counts;
  }, [allFiles]);
  const selectedItems = useMemo(
    () => items.filter((item) => selectedPaths.has(item.path)),
    [items, selectedPaths]
  );
  const selectedFiles = useMemo(
    () => selectedItems.filter((item) => item.type === "file"),
    [selectedItems]
  );
  const selectedFolders = useMemo(
    () => selectedItems.filter((item) => item.type === "folder"),
    [selectedItems]
  );
  const pendingCanResume = pendingBatch !== null
    && (storageAvailable || canPackStagedArchivePart(pendingBatch));

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await relay.request<StorageListResponse>(
        "storage.list",
        { path },
        STORAGE_LIST_TIMEOUT_MS
      );
      setItems(response.items);
      setSelectedPaths((current) => {
        const availablePaths = new Set(response.items.map((item) => item.path));
        return new Set([...current].filter((selectedPath) => availablePaths.has(selectedPath)));
      });
    } catch (caught) {
      setItems([]);
      setError(caught instanceof Error ? caught.message : "The phone is offline.");
    } finally {
      setLoading(false);
    }
  }, [path, relay]);

  useEffect(() => { void refresh(); }, [refresh]);

  useEffect(() => relay.subscribe((status) => {
    setDeviceOnline(status.deviceOnline);
    setStorageAvailable(status.deviceOnline && status.storageSharingEnabled);
  }), [relay]);

  useEffect(() => {
    setSelectionMode(false);
    setSelectedPaths(new Set());
    setSearchQuery("");
  }, [path]);

  useEffect(() => {
    let cancelled = false;
    setDownloadQueueReady(false);
    setPendingBatch(null);
    void loadDownloadBatch(queueOwnerId)
      .catch(() => null)
      .then(async (batch) => {
        if (cancelled) return;
        if (!batch) await clearStagedArchiveFiles(queueOwnerId).catch(() => undefined);
        setPendingBatch(batch);
        setDownloadQueueReady(true);
      });
    return () => {
      cancelled = true;
    };
  }, [queueOwnerId]);

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

  function toggleAllItems() {
    setSelectedPaths((current) => current.size === sortedItems.length
      ? new Set()
      : new Set(sortedItems.map((item) => item.path)));
  }

  function leaveSelectionMode() {
    setSelectionMode(false);
    setSelectedPaths(new Set());
  }

  function startTransferMeter() {
    const now = performance.now();
    transferMeterRef.current = {
      startedAt: now,
      confirmedBytes: 0,
      lastUiUpdateAt: now,
      samples: [{ time: now, bytes: 0 }]
    };
    setTransferSpeedBps(null);
  }

  function reportTransferBytes(totalBytes: number, force = false) {
    const meter = transferMeterRef.current;
    if (!meter) return;
    const now = performance.now();
    const lastSample = meter.samples.at(-1);
    if (!lastSample || force || now - lastSample.time >= 100) {
      meter.samples.push({ time: now, bytes: totalBytes });
    } else {
      lastSample.bytes = totalBytes;
    }
    meter.samples = meter.samples.filter((sample) => now - sample.time <= TRANSFER_SAMPLE_WINDOW_MS);
    if (!force && now - meter.lastUiUpdateAt < TRANSFER_UI_INTERVAL_MS) return;

    const firstSample = meter.samples[0];
    const elapsedMs = Math.max(now - firstSample.time, 1);
    const transferredBytes = Math.max(totalBytes - firstSample.bytes, 0);
    meter.lastUiUpdateAt = now;
    if (elapsedMs >= 250 && transferredBytes > 0) {
      setTransferSpeedBps(transferredBytes * 1_000 / elapsedMs);
    }
  }

  async function downloadBatchFile(item: FileItem, signal: AbortSignal) {
    const meter = transferMeterRef.current;
    const baseBytes = meter?.confirmedBytes ?? 0;
    const file = await relay.download(item.path, {
      signal,
      onProgress: (receivedBytes) => reportTransferBytes(baseBytes + receivedBytes)
    });
    if (meter) meter.confirmedBytes = baseBytes + file.blob.size;
    reportTransferBytes(baseBytes + file.blob.size, true);
    return file;
  }

  async function checkpointBatch(
    batch: DownloadBatch,
    nextIndex: number,
    nextPart = batch.nextPart
  ): Promise<DownloadBatch> {
    const checkpoint = { ...batch, nextIndex, nextPart };
    await saveDownloadProgress(queueOwnerId, batch.id, { nextIndex, nextPart });
    setPendingBatch(checkpoint);
    return checkpoint;
  }

  async function downloadIndividualBatch(batch: DownloadBatch, signal: AbortSignal) {
    for (let index = batch.nextIndex; index < batch.files.length; index += 1) {
      throwIfCancelled(signal);
      const item = batch.files[index];
      setBulkProgress({
        completed: index,
        total: batch.files.length,
        label: item.name,
        phase: "downloading"
      });
      const file = await downloadBatchFile(item, signal);
      saveBlob(file.blob, file.name);
      await checkpointBatch(batch, index + 1);
      await shortReceiverPause(signal);
      if ((index + 1) % RECEIVER_BATCH_SIZE === 0 && index + 1 < batch.files.length) {
        setBulkProgress({
          completed: index + 1,
          total: batch.files.length,
          label: "Pausing briefly to keep this device responsive…",
          phase: "downloading"
        });
        await receiverPause(signal);
      }
    }
  }

  async function downloadZipBatch(batch: DownloadBatch, signal: AbortSignal) {
    const parts = splitArchiveParts(batch.files);
    const firstPendingPart = batch.nextPart ?? inferCompletedArchiveParts(parts, batch.nextIndex);
    let nextIndex = batch.nextIndex;
    for (let partIndex = firstPendingPart; partIndex < parts.length; partIndex += 1) {
      const part = parts[partIndex];
      const { start: partStart, end: partEnd } = archivePartBounds(parts, partIndex);
      for (let index = partStart; index < partEnd; index += 1) {
        const item = batch.files[index];
        throwIfCancelled(signal);
        let staged = await loadStagedArchiveFile(queueOwnerId, batch.id, index);
        if (!staged) {
          setBulkProgress({
            completed: index,
            total: batch.files.length,
            label: item.archivePath,
            phase: "downloading"
          });
          const file = await downloadBatchFile(item, signal);
          try {
            await saveStagedArchiveFile(queueOwnerId, batch.id, index, file.blob);
          } catch {
            throw new Error("The browser could not preserve this file for resume. Free some browser storage and try again.");
          }
          staged = file.blob;
        }
        const checkpointIndex = Math.max(nextIndex, index + 1);
        if (checkpointIndex !== nextIndex) {
          nextIndex = checkpointIndex;
          await checkpointBatch(batch, nextIndex, partIndex);
        }
        await shortReceiverPause(signal);
      }

      const archive: AsyncZippable = {};
      for (const [itemIndex, item] of part.entries()) {
        throwIfCancelled(signal);
        const staged = await loadStagedArchiveFile(queueOwnerId, batch.id, partStart + itemIndex);
        if (!staged) {
          throw new Error(`The staged copy of ${item.name} is unavailable. Reconnect the phone to download it again.`);
        }
        const data = new Uint8Array(await staged.arrayBuffer());
        const modified = validArchiveDate(item.lastModified);
        archive[item.archivePath] = modified ? [data, { mtime: modified }] : data;
      }
      setBulkProgress({
        completed: partEnd,
        total: batch.files.length,
        label: parts.length > 1 ? `Creating ZIP part ${partIndex + 1} of ${parts.length}…` : "Creating ZIP…",
        phase: "packing"
      });
      const zipped = await createZip(archive);
      throwIfCancelled(signal);
      const zipName = archiveName(batch.folderPath, partIndex + 1, parts.length);
      saveBlob(new Blob([zipped], { type: "application/zip" }), zipName);
      await checkpointBatch(batch, nextIndex, partIndex + 1);
      await deleteStagedArchiveFiles(
        queueOwnerId,
        batch.id,
        part.map((_item, itemIndex) => partStart + itemIndex)
      );
      if (partIndex + 1 < parts.length) await receiverPause(signal);
    }
  }

  async function runBatch(batch: DownloadBatch, signal: AbortSignal) {
    if (batch.mode === "individual") await downloadIndividualBatch(batch, signal);
    else await downloadZipBatch(batch, signal);
  }

  async function startOrResumeBatch(batch: DownloadBatch, successNotice?: string) {
    if (batchRunActiveRef.current || bulkProgress) return;
    batchRunActiveRef.current = true;
    const abortController = new AbortController();
    batchAbortControllerRef.current = abortController;
    startTransferMeter();
    setError(null);
    setNotice(null);
    try {
      await runBatch(batch, abortController.signal);
      await clearDownloadBatch(queueOwnerId);
      await clearStagedArchiveFiles(queueOwnerId, batch.id);
      setPendingBatch(null);
      const parts = splitArchiveParts(batch.files);
      setNotice(successNotice ?? (
        batch.mode === "individual"
          ? `Finished ${batch.files.length} file downloads. If only one appears, allow multiple downloads in your browser.`
          : parts.length > 1
            ? `Downloaded ${batch.files.length} files in ${parts.length} smaller ZIP parts.`
            : `Downloaded ${batch.files.length} files as ${archiveName(batch.folderPath, 1, 1)}.`
      ));
      leaveSelectionMode();
    } catch (caught) {
      if (isAbortError(caught)) {
        await clearDownloadBatch(queueOwnerId);
        await clearStagedArchiveFiles(queueOwnerId, batch.id);
        setPendingBatch(null);
        setError(null);
        setNotice(batch.mode === "zip"
          ? "Download cancelled. ZIPs already saved were kept; staged browser copies were removed."
          : "Download cancelled. Files already saved on this device were kept.");
        leaveSelectionMode();
        return;
      }
      const saved = await loadDownloadBatch(queueOwnerId) ?? batch;
      setPendingBatch(saved);
      const completed = saved.nextIndex;
      const detail = caught instanceof Error ? caught.message : "The connection was interrupted.";
      setError(
        `Download paused after ${completed} of ${saved.files.length}. ${detail} Resume to continue without repeating ${saved.mode === "zip" ? "staged files or completed ZIP parts" : "completed files"}.`
      );
    } finally {
      if (batchAbortControllerRef.current === abortController) {
        batchAbortControllerRef.current = null;
      }
      batchRunActiveRef.current = false;
      transferMeterRef.current = null;
      setBulkProgress(null);
    }
  }

  function cancelActiveBatch() {
    const abortController = batchAbortControllerRef.current;
    if (!abortController) return;
    const message = bulkProgress?.phase === "scanning"
      ? "Cancel preparing the selected folders? Nothing will be downloaded."
      : "Cancel the remaining downloads? Files already downloaded will stay on this device.";
    if (!window.confirm(message)) return;
    abortController.abort();
  }

  useEffect(() => {
    if (!pendingCanResume) {
      autoResumeAttemptedRef.current = false;
      return;
    }
    if (!downloadQueueReady || !pendingBatch || bulkProgress || autoResumeAttemptedRef.current) return;

    autoResumeAttemptedRef.current = true;
    void startOrResumeBatch(pendingBatch);
  }, [bulkProgress, downloadQueueReady, pendingBatch, pendingCanResume]);

  async function handleIndividualDownloads() {
    if (!downloadQueueReady || selectedFiles.length === 0 || selectedFolders.length > 0 || bulkProgress || pendingBatch) return;
    const batch = createDownloadBatch("individual", path, selectedFiles);
    await saveDownloadBatch(queueOwnerId, batch);
    setPendingBatch(batch);
    await startOrResumeBatch(batch);
  }

  async function handleZipDownload() {
    if (!downloadQueueReady || selectedItems.length === 0 || bulkProgress || pendingBatch) return;
    const scanAbortController = new AbortController();
    batchAbortControllerRef.current = scanAbortController;
    setError(null);
    setNotice(null);
    setBulkProgress({
      completed: 0,
      total: selectedItems.length,
      label: "Finding files in the selected folders…",
      phase: "scanning"
    });
    try {
      const archiveFiles = await collectSelectedArchiveFiles(
        selectedItems,
        path,
        async (folderPath) => {
          const response = await abortablePromise(
            relay.request<StorageListResponse>(
              "storage.list",
              { path: folderPath },
              STORAGE_LIST_TIMEOUT_MS
            ),
            scanAbortController.signal
          );
          return response.items;
        },
        {
          signal: scanAbortController.signal,
          onProgress: ({ foldersScanned, filesFound, currentPath }) => setBulkProgress({
            completed: foldersScanned,
            total: Math.max(foldersScanned + 1, selectedItems.length),
            label: `${filesFound} files found · ${currentPath}`,
            phase: "scanning"
          })
        }
      );
      if (archiveFiles.length === 0) {
        setNotice("The selected folders are empty. Nothing was downloaded.");
        return;
      }
      await requestPersistentArchiveStorage();
      const batch = createDownloadBatch("zip", path, archiveFiles);
      await saveDownloadBatch(queueOwnerId, batch);
      setPendingBatch(batch);
      setBulkProgress(null);
      if (batchAbortControllerRef.current === scanAbortController) {
        batchAbortControllerRef.current = null;
      }
      await startOrResumeBatch(batch);
    } catch (caught) {
      if (isAbortError(caught)) {
        setNotice("Folder preparation cancelled. Nothing was downloaded.");
      } else {
        setError(caught instanceof Error ? caught.message : "Could not prepare the selected folders.");
      }
    } finally {
      if (batchAbortControllerRef.current === scanAbortController) {
        batchAbortControllerRef.current = null;
      }
      setBulkProgress(null);
    }
  }

  async function handleDownloadRemaining() {
    if (!downloadQueueReady || selectedFiles.length === 0 || selectedFolders.length > 0 || bulkProgress || pendingBatch) return;
    const picker = (window as DirectoryPickerWindow).showDirectoryPicker;
    const selection = [...selectedFiles];
    setError(null);
    setNotice(null);
    try {
      let directoryName: string;
      let diskNames: Set<string>;

      const updateScanProgress = (fileCount: number) => {
        setBulkProgress({
          completed: 0,
          total: selection.length,
          label: `${new Intl.NumberFormat().format(fileCount)} local filenames checked…`,
          phase: "scanning"
        });
      };

      if (picker) {
        const directory = await picker.call(window, {
          id: "between-downloads-folder",
          mode: "read",
          startIn: "downloads"
        });
        directoryName = directory.name;
        setBulkProgress({
          completed: 0,
          total: selection.length,
          label: `Checking ${directoryName} and its subfolders…`,
          phase: "scanning"
        });
        diskNames = await collectDirectoryFileNames(directory, updateScanProgress);
      } else {
        const directory = await selectDirectoryFiles();
        directoryName = directory.directoryName;
        setBulkProgress({
          completed: 0,
          total: selection.length,
          label: `Checking ${directoryName} and its subfolders…`,
          phase: "scanning"
        });
        diskNames = collectSelectedFileNames(directory.files, updateScanProgress);
      }

      const remaining = filesMissingByName(selection, diskNames);
      const skipped = selection.length - remaining.length;
      if (remaining.length === 0) {
        setNotice(`All ${selection.length} selected filenames already exist in ${directoryName}.`);
        leaveSelectionMode();
        return;
      }

      const batch = createDownloadBatch("individual", path, remaining);
      await saveDownloadBatch(queueOwnerId, batch);
      setPendingBatch(batch);
      await startOrResumeBatch(
        batch,
        `Downloaded ${remaining.length} missing files and skipped ${skipped} filenames already found in ${directoryName}.`
      );
    } catch (caught) {
      if (caught instanceof DOMException && caught.name === "AbortError") {
        setNotice("Folder selection cancelled. Nothing was downloaded.");
      } else {
        setError(caught instanceof Error ? caught.message : "Could not scan the selected folder.");
      }
    } finally {
      setBulkProgress(null);
    }
  }

  async function discardPendingBatch() {
    if (!pendingBatch || !window.confirm("Discard this saved download queue? Files already downloaded will stay on this device.")) return;
    await clearDownloadBatch(queueOwnerId);
    await clearStagedArchiveFiles(queueOwnerId, pendingBatch.id);
    setPendingBatch(null);
    setError(null);
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
            {sortedItems.length > 0 ? (
              <button type="button" className="soft-button" onClick={() => setSelectionMode(true)}>
                <ListChecks aria-hidden size={16} /><span>Select</span>
              </button>
            ) : null}
          </>
        ) : (
          <div className="selection-toolbar" aria-label="Selected item actions">
            <strong className="selection-count">{selectedItems.length} selected</strong>
            <button type="button" className="soft-button" onClick={toggleAllItems} disabled={bulkProgress !== null}>
              {selectedItems.length === sortedItems.length ? "Clear all" : "Select all"}
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
                onClick={() => void handleDownloadRemaining()}
                disabled={!downloadQueueReady || selectedFiles.length === 0 || selectedFolders.length > 0 || bulkProgress !== null || pendingBatch !== null}
                title={selectedFolders.length > 0 ? "Folders can be downloaded with Download ZIP." : undefined}
              >
                <FolderSearch aria-hidden size={16} />
                <span>Download remaining</span>
              </button>
              <button
                type="button"
                className="bulk-download-button secondary"
                onClick={() => void handleIndividualDownloads()}
                disabled={!downloadQueueReady || selectedFiles.length === 0 || selectedFolders.length > 0 || bulkProgress !== null || pendingBatch !== null}
                title={selectedFolders.length > 0 ? "Folders can be downloaded with Download ZIP." : undefined}
              >
                <Download aria-hidden size={16} />
                <span>Download files</span>
              </button>
              <button
                type="button"
                className="bulk-download-button secondary"
                onClick={() => void handleZipDownload()}
                disabled={!downloadQueueReady || selectedItems.length === 0 || bulkProgress !== null || pendingBatch !== null}
              >
                <Archive aria-hidden size={16} />
                <span>Download ZIP</span>
              </button>
            </div>
          </div>
        )}
        {!selectionMode && items.length > 0 ? (
          <div className="file-organize-controls">
            <div className="storage-search">
              <Search className="storage-search-icon" aria-hidden size={18} />
              <label className="sr-only" htmlFor="storage-folder-search">Search file names</label>
              <input
                id="storage-folder-search"
                type="search"
                name="storage-search"
                className="storage-search-input"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.currentTarget.value)}
                onKeyDown={(event) => {
                  if (event.key === "Escape") setSearchQuery("");
                }}
                placeholder="Search file names…"
                autoComplete="off"
                spellCheck={false}
              />
              {searchQuery ? (
                <button
                  type="button"
                  className="storage-search-clear"
                  onClick={() => setSearchQuery("")}
                  aria-label="Clear file name search"
                >
                  <X aria-hidden size={17} />
                </button>
              ) : <span className="storage-search-clear-placeholder" aria-hidden />}
            </div>
            <div className="file-filter-bar">
              <span className="file-filter-label" aria-hidden>
                <ListFilter size={16} />
                <span>Type</span>
              </span>
              <div className="file-filter-scroll" role="group" aria-label="Filter files by type">
                {fileFilterOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className={fileFilter === option.value ? "file-filter-chip active" : "file-filter-chip"}
                    aria-pressed={fileFilter === option.value}
                    onClick={() => setFileFilter(option.value)}
                  >
                    <span>{option.label}</span>
                    <span
                      className="file-filter-count"
                      aria-label={`${filterCounts[option.value]} ${filterCounts[option.value] === 1 ? "file" : "files"}`}
                    >
                      {filterCounts[option.value]}
                    </span>
                  </button>
                ))}
              </div>
            </div>
            <div className="file-sort-bar">
              <span className="file-filter-label" aria-hidden>
                <ArrowUpDown size={16} />
                <span>Sort</span>
              </span>
              <label className="file-sort-select-shell">
                <span className="sr-only">Sort files by</span>
                <select
                  value={fileSort}
                  onChange={(event) => {
                    const nextSort = event.currentTarget.value as FileSort;
                    setFileSort(nextSort);
                    setSortDirection(defaultSortDirection(nextSort));
                  }}
                >
                  {fileSortOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <button
                type="button"
                className="file-sort-direction"
                onClick={() => setSortDirection((current) => current === "ascending" ? "descending" : "ascending")}
                aria-label={`Sort direction: ${sortDirectionLabel(fileSort, sortDirection)}`}
              >
                {sortDirection === "ascending"
                  ? <ArrowUp aria-hidden size={16} />
                  : <ArrowDown aria-hidden size={16} />}
                <span>{sortDirectionLabel(fileSort, sortDirection)}</span>
              </button>
            </div>
          </div>
        ) : null}
      </div>

      <div className="storage-content">
        {bulkProgress ? (
          <div className="bulk-progress">
            <div className="bulk-progress-header">
              <div className="bulk-progress-copy">
                <div className="bulk-progress-status" role="status" aria-live="polite">
                  <strong>
                    {bulkProgress.phase === "packing"
                      ? "Preparing download"
                      : bulkProgress.phase === "scanning"
                        ? "Preparing selected folders"
                        : `Downloading ${bulkProgress.completed + 1} of ${bulkProgress.total}`}
                  </strong>
                  <span className="bulk-progress-label">{bulkProgress.label}</span>
                </div>
                {bulkProgress.phase === "downloading" && transferSpeedBps !== null ? (
                  <span
                    className="bulk-progress-speed"
                    aria-label={`Current transfer speed ${formatTransferSpeed(transferSpeedBps)}`}
                  >
                    {formatTransferSpeed(transferSpeedBps)}
                  </span>
                ) : null}
              </div>
              <button
                type="button"
                className="icon-button compact-icon-button bulk-cancel-button"
                onClick={cancelActiveBatch}
                aria-label={bulkProgress.phase === "scanning" ? "Cancel folder preparation" : "Cancel remaining downloads"}
                title={bulkProgress.phase === "scanning" ? "Cancel preparation" : "Cancel download"}
              >
                <X aria-hidden size={18} />
              </button>
            </div>
            {bulkProgress.phase === "scanning"
              ? <progress />
              : <progress max={bulkProgress.total} value={bulkProgress.completed} />}
          </div>
        ) : null}

        {pendingBatch && !bulkProgress ? (
          <div className="download-resume-card" role="status" aria-live="polite">
            <div className="download-resume-icon" aria-hidden>
              {deviceOnline ? <RotateCcw size={19} /> : <WifiOff size={19} />}
            </div>
            <div className="download-resume-copy">
              <strong>{pendingBatch.files.length - pendingBatch.nextIndex} {pendingBatch.mode === "zip" ? "files" : "downloads"} waiting</strong>
              <span>
                {pendingBatch.mode === "zip"
                  ? `${pendingBatch.nextIndex} of ${pendingBatch.files.length} files staged; ${pendingBatch.nextPart ?? inferCompletedArchiveParts(splitArchiveParts(pendingBatch.files), pendingBatch.nextIndex)} ZIP parts saved.`
                  : `${pendingBatch.nextIndex} of ${pendingBatch.files.length} completed.`}
                {pendingCanResume ? " Progress is saved in this browser." : " It will resume when the phone is available."}
              </span>
            </div>
            <div className="download-resume-actions">
              <button
                type="button"
                className="bulk-download-button"
                onClick={() => void startOrResumeBatch(pendingBatch)}
                disabled={!pendingCanResume}
              >
                <RotateCcw aria-hidden size={16} />
                <span>{pendingCanResume ? "Resume" : deviceOnline ? "Files paused" : "Waiting for phone"}</span>
              </button>
              <button type="button" className="soft-button" onClick={() => void discardPendingBatch()}>
                <Trash2 aria-hidden size={15} />
                <span>Discard</span>
              </button>
            </div>
          </div>
        ) : null}

        {notice ? <div className="bulk-success" role="status" aria-live="polite">{notice}</div> : null}

        {loading ? <div className="storage-state" role="status">Loading storage…</div> : null}
        {!loading && error ? <div className="storage-state warning" role="status">{error}</div> : null}
        {!loading && !error && items.length === 0 ? <div className="storage-state" role="status">This folder is empty.</div> : null}
        {!loading && !error && items.length > 0 && filteredItems.length === 0 ? (
          <div className="storage-state filtered-empty" role="status">
            {hasSearchQuery ? (
              <>
                <span>No items match “{deferredSearchQuery.trim()}”.</span>
                <button type="button" className="soft-button" onClick={() => setSearchQuery("")}>Clear search</button>
              </>
            ) : (
              <>
                <span>No {fileFilterOptions.find((option) => option.value === fileFilter)?.label.toLowerCase()} in this folder.</span>
                <button type="button" className="soft-button" onClick={() => setFileFilter("all")}>Show all files</button>
              </>
            )}
          </div>
        ) : null}
        {!loading && !error && filteredItems.length > 0 ? (
          <ul className="file-list">
            {sortedItems.map((item) => (
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
      </div>
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
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 30_000);
}

function createZip(files: AsyncZippable): Promise<Uint8Array<ArrayBuffer>> {
  return new Promise((resolve, reject) => {
    zip(files, { level: 0 }, (error, data) => {
      if (error) reject(error);
      else resolve(data);
    });
  });
}

function archiveName(path: string, part: number, totalParts: number): string {
  const folder = path.split("/").filter(Boolean).at(-1) ?? "shared-files";
  const safeFolder = folder.replace(/[<>:"/\\|?*\u0000-\u001F]/g, "-").trim() || "shared-files";
  return totalParts > 1 ? `${safeFolder}-part-${part}-of-${totalParts}.zip` : `${safeFolder}.zip`;
}

function receiverPause(signal: AbortSignal): Promise<void> {
  return abortableDelay(RECEIVER_BATCH_PAUSE_MS, signal);
}

function validArchiveDate(value: string | null): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function shortReceiverPause(signal: AbortSignal): Promise<void> {
  return abortableDelay(INTER_FILE_PAUSE_MS, signal);
}

function abortableDelay(durationMs: number, signal: AbortSignal): Promise<void> {
  if (signal.aborted) return Promise.reject(downloadCancelledError());
  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      signal.removeEventListener("abort", handleAbort);
      resolve();
    }, durationMs);
    const handleAbort = () => {
      window.clearTimeout(timeout);
      reject(downloadCancelledError());
    };
    signal.addEventListener("abort", handleAbort, { once: true });
  });
}

function abortablePromise<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  if (signal.aborted) return Promise.reject(downloadCancelledError());
  return new Promise((resolve, reject) => {
    const handleAbort = () => {
      reject(downloadCancelledError());
    };
    signal.addEventListener("abort", handleAbort, { once: true });
    void promise.then(
      (value) => {
        signal.removeEventListener("abort", handleAbort);
        resolve(value);
      },
      (error: unknown) => {
        signal.removeEventListener("abort", handleAbort);
        reject(error);
      }
    );
  });
}

function throwIfCancelled(signal: AbortSignal): void {
  if (signal.aborted) throw downloadCancelledError();
}

function downloadCancelledError(): Error {
  const error = new Error("Download cancelled.");
  error.name = "AbortError";
  return error;
}

function isAbortError(error: unknown): error is Error {
  return error instanceof Error && error.name === "AbortError";
}

function formatTransferSpeed(bytesPerSecond: number): string {
  if (bytesPerSecond < 1024) return `${transferNumberFormatter.format(bytesPerSecond)}\u00a0B/s`;
  if (bytesPerSecond < 1024 * 1024) {
    return `${transferNumberFormatter.format(bytesPerSecond / 1024)}\u00a0KB/s`;
  }
  return `${transferNumberFormatter.format(bytesPerSecond / 1024 / 1024)}\u00a0MB/s`;
}

function canPackStagedArchivePart(batch: DownloadBatch): boolean {
  if (batch.mode !== "zip") return false;
  const parts = splitArchiveParts(batch.files);
  const partIndex = batch.nextPart ?? inferCompletedArchiveParts(parts, batch.nextIndex);
  if (partIndex >= parts.length) return true;
  return batch.nextIndex >= archivePartBounds(parts, partIndex).end;
}
