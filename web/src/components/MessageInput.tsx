import { FolderOpen, Send } from "lucide-react";
import { FormEvent, KeyboardEvent, useState } from "react";

interface MessageInputProps {
  disabled?: boolean;
  onOpenStorage: () => void;
  onSend: (text: string) => Promise<void>;
}

export function MessageInput({ disabled = false, onOpenStorage, onSend }: MessageInputProps) {
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const trimmed = text.trim();

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (sending || !trimmed) {
      return;
    }
    setSending(true);
    try {
      await onSend(trimmed);
      setText("");
    } finally {
      setSending(false);
    }
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
          onChange={(event) => setText(event.target.value)}
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
