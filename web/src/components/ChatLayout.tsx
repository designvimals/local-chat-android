import { HardDrive, LogOut, MessageCircle, RefreshCw, ShieldCheck } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { clearSession } from "../lib/auth";
import { loadLocalMessages, mergeMessages, saveLocalMessages } from "../lib/chatStore";
import { navigate } from "../lib/navigation";
import { RelayClient } from "../lib/relay";
import type { AuthSession, Message } from "../types/api";
import { MessageInput } from "./MessageInput";
import { MessageList } from "./MessageList";
import { StoragePanel } from "./StoragePanel";

interface ChatLayoutProps {
  route: "/chat" | "/storage";
  session: AuthSession;
  onSignedOut: () => void;
}

export function ChatLayout({ route, session, onSignedOut }: ChatLayoutProps) {
  const [relay] = useState(() => new RelayClient(session.pairedToken));
  const [messages, setMessages] = useState<Message[]>(() => loadLocalMessages(session));
  const messagesRef = useRef(messages);
  const syncingRef = useRef(false);
  const [relayConnected, setRelayConnected] = useState(false);
  const [online, setOnline] = useState(false);
  const [storageEnabled, setStorageEnabled] = useState(false);
  const [lastSeen, setLastSeen] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [remoteTyping, setRemoteTyping] = useState(false);
  const typingStateRef = useRef(false);

  const commitMessages = useCallback((next: Message[]) => {
    const sorted = [...next].sort((a, b) => a.timestamp.localeCompare(b.timestamp));
    messagesRef.current = sorted;
    saveLocalMessages(session, sorted);
    setMessages(sorted);
  }, [session]);

  const sync = useCallback(async () => {
    if (syncingRef.current || !relay.isDeviceOnline()) {
      setLoading(false);
      return;
    }
    syncingRef.current = true;
    try {
      let remote = await relay.request<{ messages: Message[]; typing?: boolean }>("chat.sync", {
        readerDeviceId: session.viewerDeviceId
      });
      setRemoteTyping(remote.typing === true);
      let merged = mergeMessages(messagesRef.current, remote.messages);
      commitMessages(merged);

      const queued = merged.filter(
        (message) => message.senderDeviceId === session.viewerDeviceId &&
          (message.status === "pending" || message.status === "failed")
      );
      for (const message of queued) {
        try {
          await relay.request("chat.send", { message });
          merged = merged.map((candidate) => candidate.id === message.id
            ? { ...candidate, status: "delivered" as const, deliveredAt: new Date().toISOString() }
            : candidate);
          commitMessages(merged);
        } catch {
          break;
        }
      }

      if (document.visibilityState === "visible") {
        await relay.request("chat.read", {
          readerDeviceId: session.viewerDeviceId,
          readAt: new Date().toISOString()
        });
      }
      remote = await relay.request<{ messages: Message[]; typing?: boolean }>("chat.sync", {
        readerDeviceId: session.viewerDeviceId
      });
      setRemoteTyping(remote.typing === true);
      commitMessages(mergeMessages(messagesRef.current, remote.messages));
      setError(null);
      setLastSeen(new Date().toISOString());
    } catch (caught) {
      if (messagesRef.current.length === 0) {
        setError(caught instanceof Error ? caught.message : "The phone is offline.");
      }
    } finally {
      setLoading(false);
      syncingRef.current = false;
    }
  }, [commitMessages, relay, session.viewerDeviceId]);

  useEffect(() => {
    const unsubscribe = relay.subscribe((status) => {
      setRelayConnected(status.relayConnected);
      setOnline(status.deviceOnline);
      setStorageEnabled(status.storageSharingEnabled);
      if (status.deviceOnline) setLastSeen(new Date().toISOString());
    });
    relay.connect();
    return () => {
      unsubscribe();
      relay.close();
    };
  }, [relay]);

  useEffect(() => {
    if (online) void sync();
    const timer = window.setInterval(() => void sync(), 4000);
    const onVisible = () => {
      if (document.visibilityState === "visible") void sync();
    };
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("online", onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("online", onVisible);
    };
  }, [online, sync]);

  const statusText = useMemo(() => {
    if (online) return storageEnabled ? "Online · Files available" : "Online · Files paused";
    if (!relayConnected) return "Reconnecting · Messages will wait";
    if (!lastSeen) return "Phone offline · Messages will wait";
    return `Last connected ${new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(lastSeen))}`;
  }, [lastSeen, online, relayConnected, storageEnabled]);

  async function handleSend(text: string) {
    const now = new Date().toISOString();
    const message: Message = {
      id: crypto.randomUUID(),
      senderDeviceId: session.viewerDeviceId,
      receiverDeviceId: "storage-owner-phone",
      text,
      timestamp: now,
      status: "pending",
      updatedAt: now
    };
    commitMessages([...messagesRef.current, message]);
    void sync();
  }

  const handleTyping = useCallback((typing: boolean) => {
    if (typingStateRef.current === typing) return;
    typingStateRef.current = typing;
    if (relay.isDeviceOnline()) {
      void relay.request("chat.typing", { typing }).catch(() => undefined);
    }
  }, [relay]);

  function signOut() {
    relay.close();
    clearSession();
    onSignedOut();
    navigate("/login");
  }

  const storageOpen = route === "/storage";
  const compactStorage = storageOpen && window.matchMedia("(max-width: 760px)").matches;
  if (compactStorage) {
    return (
      <main id="main" className="mobile-storage-page">
        <StoragePanel
          relay={relay}
          queueOwnerId={session.viewerDeviceId}
          fullScreen
          onClose={() => navigate("/chat")}
        />
      </main>
    );
  }

  return (
    <div className={storageOpen ? "app-shell storage-open" : "app-shell"}>
      <aside className="friend-rail" aria-label="Private connection">
        <div className="brand-lockup">
          <span className="brand-mark" aria-hidden><MessageCircle size={22} /></span>
          <span>Between</span>
        </div>
        <div className="connection-card">
          <div className="friend-avatar" aria-hidden>{session.friendName.slice(0, 1).toUpperCase()}</div>
          <div className="friend-copy">
            <strong>{session.friendName}</strong>
            <span className={online ? "presence online" : "presence"}>{online ? "Online" : "Offline"}</span>
          </div>
        </div>
        <div className="privacy-note">
          <ShieldCheck aria-hidden size={18} />
          <p><strong>Device-only messages</strong><span>Messages stay on your devices.</span></p>
        </div>
        <button type="button" className="rail-button" onClick={signOut}>
          <LogOut aria-hidden size={16} /><span>Disconnect</span>
        </button>
      </aside>

      <main id="main" className="chat-panel">
        <header className="chat-header">
          <div className="chat-identity">
            <div className="mobile-avatar" aria-hidden>{session.friendName.slice(0, 1).toUpperCase()}</div>
            <div>
              <p className="eyebrow">Private conversation</p>
              <h1>{session.friendName}</h1>
              <p className="chat-status"><span className={online ? "status-dot online" : "status-dot"} />{statusText}</p>
            </div>
          </div>
          <div className="header-actions">
            <button type="button" className="icon-button" onClick={() => void sync()} aria-label="Sync chat">
              <RefreshCw aria-hidden size={18} />
            </button>
            <button type="button" className="folder-button" onClick={() => navigate("/storage")} aria-label="Open files">
              <HardDrive aria-hidden size={18} /><span>Files</span>
            </button>
          </div>
        </header>

        <div className="chat-body">
          {error ? <div className="connection-banner" role="status">{error}</div> : null}
          {loading && messages.length === 0 ? <div className="empty-state" role="status">Opening your local conversation…</div> : null}
          {!loading || messages.length > 0 ? (
            <MessageList
              messages={messages}
              viewerDeviceId={session.viewerDeviceId}
              relay={relay}
              remoteTyping={remoteTyping}
            />
          ) : null}
        </div>

        <div className="composer-shell">
          {!online ? <p className="queue-hint">Offline — send now and it will leave when the phone reconnects.</p> : null}
          <MessageInput onOpenStorage={() => navigate("/storage")} onSend={handleSend} onTyping={handleTyping} />
        </div>
      </main>

      {storageOpen ? (
        <StoragePanel
          relay={relay}
          queueOwnerId={session.viewerDeviceId}
          onClose={() => navigate("/chat")}
        />
      ) : null}
    </div>
  );
}
