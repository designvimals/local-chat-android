import { Check, CheckCheck, CircleAlert, Clock3, Download, File, LoaderCircle, Trash2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ChatAttachment, Message } from "../../../shared/api-contracts/types";
import { singleEmojiOrNull } from "../lib/messagePresentation";
import { canonicalAttachments } from "../lib/chatStore";
import { RelayClient } from "../lib/relay";

interface MessageListProps {
  messages: Message[];
  viewerDeviceId: string;
  relay: RelayClient;
  remoteTyping: boolean;
  onDeleteMessage: (messageId: string) => void;
}

const timeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: "numeric",
  minute: "2-digit"
});

export function MessageList({ messages, viewerDeviceId, relay, remoteTyping, onDeleteMessage }: MessageListProps) {
  const endRef = useRef<HTMLDivElement>(null);
  const rowRefs = useRef(new Map<string, HTMLElement>());
  const [highlightedId, setHighlightedId] = useState<string | null>(null);
  const visibleMessages = useMemo(
    () => messages.filter((message) => !(message.deletedForDeviceIds ?? []).includes(viewerDeviceId)),
    [messages, viewerDeviceId]
  );
  const messageById = useMemo(() => new Map(messages.map((message) => [message.id, message])), [messages]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length]);

  if (visibleMessages.length === 0) {
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
      {visibleMessages.map((message) => {
        const mine = message.senderDeviceId === viewerDeviceId;
        const attachments = canonicalAttachments(message);
        const emoji = !message.deletedAt && attachments.length === 0 ? singleEmojiOrNull(message.text) : null;
        const rawReplyTarget = message.replyToMessageId ? messageById.get(message.replyToMessageId) : undefined;
        const replyTarget = rawReplyTarget && !(rawReplyTarget.deletedForDeviceIds ?? []).includes(viewerDeviceId)
          ? rawReplyTarget
          : undefined;
        return (
          <li
            className={`${mine ? "message-row mine" : "message-row"}${highlightedId === message.id ? " highlighted" : ""}`}
            key={message.id}
            ref={(element) => {
              if (element) rowRefs.current.set(message.id, element);
              else rowRefs.current.delete(message.id);
            }}
          >
            <article className={`message-bubble${emoji ? " emoji-only" : ""}${message.deletedAt ? " deleted" : ""}`}>
              {message.replyToMessageId ? (
                <button
                  type="button"
                  className="reply-quote"
                  onClick={() => {
                    const target = rowRefs.current.get(message.replyToMessageId!);
                    if (!target) return;
                    target.scrollIntoView({ block: "center", behavior: "smooth" });
                    setHighlightedId(message.replyToMessageId!);
                    window.setTimeout(() => setHighlightedId(null), 900);
                  }}
                >
                  <strong>{replyTarget?.senderDeviceId === viewerDeviceId ? "You" : "Phone"}</strong>
                  <span>{replySummary(replyTarget)}</span>
                </button>
              ) : null}
              {message.deletedAt ? (
                <p className="deleted-copy"><Trash2 aria-hidden size={15} />This message was deleted.</p>
              ) : (
                <>
                  {attachments.length ? <AttachmentGroup attachments={attachments} relay={relay} /> : null}
                  {emoji ? <p className="single-emoji">{emoji}</p> : message.text ? <p>{message.text}</p> : null}
                </>
              )}
              <footer>
                {message.editedAt ? <span>edited</span> : null}
                <button
                  type="button"
                  className="message-delete-button"
                  onClick={() => onDeleteMessage(message.id)}
                  aria-label={`Delete message from this browser, sent ${timeFormatter.format(new Date(message.timestamp))}`}
                  title="Delete from this browser"
                >
                  <Trash2 aria-hidden size={13} />
                </button>
                <time dateTime={message.timestamp}>{timeFormatter.format(new Date(message.timestamp))}</time>
                {mine ? <MessageReceipt status={message.status} /> : null}
              </footer>
              {!message.deletedAt && message.reactions?.length ? (
                <div className="reaction-list" aria-label="Reactions">
                  {message.reactions.map((reaction) => (
                    <span key={reaction.emoji} aria-label={`${reaction.emoji}, ${reaction.reactorDeviceIds.length} reactions`}>
                      {reaction.emoji} {reaction.reactorDeviceIds.length}
                    </span>
                  ))}
                </div>
              ) : null}
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

function replySummary(message: Message | undefined): string {
  if (!message) return "Original message unavailable";
  if (message.deletedAt) return "This message was deleted.";
  if (message.text.trim()) return message.text;
  const attachments = canonicalAttachments(message);
  if (attachments.length === 1) return attachments[0]!.name;
  const imageCount = attachments.filter((attachment) => attachment.mimeType.startsWith("image/")).length;
  if (imageCount === attachments.length) return `${imageCount} photos`;
  if (attachments.length > 1) return `${attachments.length} attachments`;
  return "Original message unavailable";
}

function AttachmentGroup({ attachments, relay }: { attachments: ChatAttachment[]; relay: RelayClient }) {
  const images = attachments.filter((attachment) => attachment.mimeType.startsWith("image/"));
  const files = attachments.filter((attachment) => !attachment.mimeType.startsWith("image/"));
  return (
    <div className="attachment-group">
      {images.length ? (
        <div className={`attachment-gallery${images.length > 1 ? " multiple" : ""}`} aria-label={`${images.length} attached ${images.length === 1 ? "image" : "images"}`}>
          {images.map((attachment) => <AttachmentPreview key={attachment.id} attachment={attachment} relay={relay} />)}
        </div>
      ) : null}
      {files.map((attachment) => <AttachmentPreview key={attachment.id} attachment={attachment} relay={relay} />)}
    </div>
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
        <img
          src={file.url}
          alt={attachment.name}
          width={attachment.width}
          height={attachment.height}
        />
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
