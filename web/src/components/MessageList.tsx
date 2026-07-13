import { Check, CheckCheck, CircleAlert, Clock3 } from "lucide-react";
import { useEffect, useRef } from "react";
import type { Message } from "../types/api";

interface MessageListProps {
  messages: Message[];
  viewerDeviceId: string;
}

const timeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: "numeric",
  minute: "2-digit"
});

export function MessageList({ messages, viewerDeviceId }: MessageListProps) {
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
              <p>{message.text}</p>
              <footer>
                <time dateTime={message.timestamp}>{timeFormatter.format(new Date(message.timestamp))}</time>
                {mine ? <MessageReceipt status={message.status} /> : null}
              </footer>
            </article>
          </li>
        );
      })}
      <li className="scroll-anchor" aria-hidden><div ref={endRef} /></li>
    </ol>
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
