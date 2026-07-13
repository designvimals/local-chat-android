import { ArrowLeft, Home, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { RelayClient } from "../lib/relay";
import type { FileItem, StorageListResponse } from "../types/api";
import { FileRow } from "./FileRow";

interface StoragePanelProps {
  relay: RelayClient;
  fullScreen?: boolean;
  onClose: () => void;
}

export function StoragePanel({ relay, fullScreen = false, onClose }: StoragePanelProps) {
  const [path, setPath] = useState("/");
  const [items, setItems] = useState<FileItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [downloadingPath, setDownloadingPath] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const breadcrumb = useMemo(() => path === "/"
    ? ["Shared folders"]
    : ["Shared folders", ...path.split("/").filter(Boolean)], [path]);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await relay.request<StorageListResponse>("storage.list", { path });
      setItems(response.items);
    } catch (caught) {
      setItems([]);
      setError(caught instanceof Error ? caught.message : "The phone is offline.");
    } finally {
      setLoading(false);
    }
  }, [path, relay]);

  useEffect(() => { void refresh(); }, [refresh]);

  async function handleDownload(item: FileItem) {
    setDownloadingPath(item.path);
    setError(null);
    try {
      const file = await relay.download(item.path);
      const objectUrl = URL.createObjectURL(file.blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = file.name;
      anchor.rel = "noopener";
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Download failed.");
    } finally {
      setDownloadingPath(null);
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
        <button type="button" onClick={() => setPath("/")} className="soft-button">
          <Home aria-hidden size={16} /><span>Shared folders</span>
        </button>
      </div>

      {loading ? <div className="storage-state" role="status">Loading storage…</div> : null}
      {!loading && error ? <div className="storage-state warning" role="status">{error}</div> : null}
      {!loading && !error && items.length === 0 ? <div className="storage-state" role="status">This folder is empty.</div> : null}
      {!loading && !error && items.length > 0 ? (
        <ul className="file-list">
          {items.map((item) => (
            <FileRow
              key={item.path}
              item={item}
              downloading={downloadingPath === item.path}
              onOpenFolder={(folder) => setPath(folder.path)}
              onDownload={(file) => void handleDownload(file)}
            />
          ))}
        </ul>
      ) : null}
    </section>
  );
}
