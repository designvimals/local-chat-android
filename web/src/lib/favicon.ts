export type FaviconStatus = "online" | "offline" | "connecting";

const STATUS_COLORS: Record<FaviconStatus, string> = {
  online: "#2d9b63",
  offline: "#87928d",
  connecting: "#d49a3a"
};

export function buildFaviconDataUri(status: FaviconStatus): string {
  const color = STATUS_COLORS[status];
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" rx="9" fill="#2f6f73"/><path d="M7 11h8l2 2h8v8H7z" fill="#fffdf8"/><circle cx="24" cy="23" r="6" fill="#fffdf8"/><circle cx="24" cy="23" r="4" fill="${color}"/></svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

export function setFaviconStatus(status: FaviconStatus): void {
  if (typeof document === "undefined") return;
  const link = document.querySelector<HTMLLinkElement>("#app-favicon") ?? createFaviconLink();
  link.href = buildFaviconDataUri(status);
}

function createFaviconLink(): HTMLLinkElement {
  const link = document.createElement("link");
  link.id = "app-favicon";
  link.rel = "icon";
  document.head.append(link);
  return link;
}
