export function singleEmojiOrNull(value: string): string | null {
  const text = value.trim();
  if (!text) return null;
  const segments = typeof Intl.Segmenter === "function"
    ? [...new Intl.Segmenter(undefined, { granularity: "grapheme" }).segment(text)]
    : [...text].map((segment) => ({ segment }));
  if (segments.length !== 1 || segments[0]?.segment !== text) return null;
  if (/^[0-9#*]$/u.test(text)) return null;
  return /\p{Emoji}/u.test(text) ? text : null;
}
