import { Check, CheckCheck, CircleAlert, Clock3, Download, File, LoaderCircle } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import type { ChatAttachment, Message } from "../../../shared/api-contracts/types";
import { RelayClient } from "../lib/relay";

interface MessageListProps {
  messages: Message[];
  viewerDeviceId: string;
  relay: RelayClient;
  remoteTyping: boolean;
}

const timeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: "numeric",
  minute: "2-digit"
});

export function MessageList({ messages, viewerDeviceId, relay, remoteTyping }: MessageListProps) {
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  if (messages.length === 0) {
    return (
      <div className="empty-conversation" role="status">
        <span className="empty-orbit" aria-hidden>↗</span>
        <h2>Your quiet corner</h2>
        <p>Send the first message. It stays here if the phone is offline and moves when both devices reconnect.</p>
      </div>
    );
  }

  return (
    <ol className="message-list" aria-label="Messages">
      {messages.map((message) => {
        const mine = message.senderDeviceId === viewerDeviceId;
        return (
          <li className={mine ? "message-row mine" : "message-row"} key={message.id}>
            <article className="message-bubble">
              {message.attachment ? <AttachmentPreview attachment={message.attachment} relay={relay} /> : null}
              {message.text ? <p>{message.text}</p> : null}
              <footer>
                <time dateTime={message.timestamp}>{timeFormatter.format(new Date(message.timestamp))}</time>
                {mine ? <MessageReceipt status={message.status} /> : null}
              </footer>
            </article>
          </li>
        );
      })}
      {remoteTyping ? (
        <li className="message-row typing-row" role="status" aria-live="polite">
          <div className="typing-bubble"><span /><span /><span />Phone is typing</div>
        </li>
      ) : null}
      <li className="scroll-anchor" aria-hidden><div ref={endRef} /></li>
    </ol>
  );
}

function AttachmentPreview({ attachment, relay }: { attachment: ChatAttachment; relay: RelayClient }) {
  const [file, setFile] = useState<{ url: string; name: string } | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [shouldLoad, setShouldLoad] = useState(false);
  const previewRef = useRef<HTMLDivElement>(null);
  const fileUrlRef = useRef<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const received = await relay.downloadAttachment(attachment.id);
      if (fileUrlRef.current) URL.revokeObjectURL(fileUrlRef.current);
      fileUrlRef.current = URL.createObjectURL(received.blob);
      setFile({ url: fileUrlRef.current, name: received.name });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Original file unavailable.");
    } finally {
      setLoading(false);
    }
  }, [attachment.id, relay]);

  useEffect(() => {
    const element = previewRef.current;
    if (!element) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        setShouldLoad(true);
        observer.disconnect();
      }
    }, { rootMargin: "240px" });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (shouldLoad) void load();
  }, [load, shouldLoad]);

  useEffect(() => () => {
    if (fileUrlRef.current) URL.revokeObjectURL(fileUrlRef.current);
  }, []);

  return (
    <div className="attachment-preview" ref={previewRef}>
      {!shouldLoad || loading ? (
        <div className="attachment-loading" role="status"><LoaderCircle aria-hidden size={18} /> Receiving original file…</div>
      ) : !file ? (
        <button type="button" className="attachment-error" onClick={() => void load()}>
          <File aria-hidden size={18} /><span>{attachment.name}<small>{error ?? "Original file unavailable."} Select to retry.</small></span>
        </button>
      ) : attachment.mimeType.startsWith("image/") ? (
        <img src={file.url} alt={attachment.name} />
      ) : (
        <div className="attachment-file"><File aria-hidden size={22} /><span>{attachment.name}</span></div>
      )}
      {file ? (
        <a href={file.url} download={file.name} aria-label={`Download original ${attachment.name}`}>
          <Download aria-hidden size={15} /> Original
        </a>
      ) : null}
    </div>
  );
}

function MessageReceipt({ status }: Pick<Message, "status">) {
  if (status === "read") {
    return <span className="receipt read" title="Read" aria-label="Read"><CheckCheck aria-hidden size={14} /></span>;
  }
  if (status === "delivered") {
    return <span className="receipt" title="Delivered" aria-label="Delivered"><CheckCheck aria-hidden size={14} /></span>;
  }
  if (status === "sent") {
    return <span className="receipt" title="Sent" aria-label="Sent"><Check aria-hidden size={14} /></span>;
  }
  if (status === "failed") {
    return <span className="receipt failed" title="Waiting to retry" aria-label="Waiting to retry"><CircleAlert aria-hidden size={13} /></span>;
  }
  return <span className="receipt" title="Waiting for phone" aria-label="Waiting for phone"><Clock3 aria-hidden size={12} /></span>;
}
