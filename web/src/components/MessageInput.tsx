import { FolderOpen, Send } from "lucide-react";
import { ChangeEvent, FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";

interface MessageInputProps {
  disabled?: boolean;
  onOpenStorage: () => void;
  onSend: (text: string) => Promise<void>;
  onTyping?: (typing: boolean) => void;
}

export function MessageInput({ disabled = false, onOpenStorage, onSend, onTyping }: MessageInputProps) {
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const trimmed = text.trim();
  const typingTimer = useRef<number | null>(null);

  useEffect(() => () => {
    if (typingTimer.current !== null) window.clearTimeout(typingTimer.current);
    onTyping?.(false);
  }, [onTyping]);

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (sending || !trimmed) {
      return;
    }
    setSending(true);
    try {
      await onSend(trimmed);
      setText("");
      onTyping?.(false);
    } finally {
      setSending(false);
    }
  }

  function handleChange(event: ChangeEvent<HTMLTextAreaElement>) {
    const next = event.target.value;
    setText(next);
    onTyping?.(next.trim().length > 0);
    if (typingTimer.current !== null) window.clearTimeout(typingTimer.current);
    typingTimer.current = window.setTimeout(() => onTyping?.(false), 1_800);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if ((event.metaKey || event.ctrlKey) && event.key === "Enter") {
      void submit();
    }
  }

  return (
    <form className="message-input" onSubmit={submit}>
      <button
        className="icon-button attachment-button"
        type="button"
        onClick={onOpenStorage}
        aria-label="Open shared files"
      >
        <FolderOpen aria-hidden size={19} />
      </button>
      <label className="composer-label">
        <span className="sr-only">Message</span>
        <textarea
          name="message"
          value={text}
          placeholder="Write a message…"
          rows={1}
          disabled={disabled}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
        />
      </label>
      <button className="send-button" type="submit" disabled={disabled || sending || !trimmed} aria-label="Send message">
        <Send aria-hidden size={18} />
        <span>{sending ? "Saving…" : "Send"}</span>
      </button>
    </form>
  );
}
