# Expressive chat implementation

## Scope

This change upgrades the native Compose chat UI without changing relay routing,
encryption, attachment storage, or the line-delimited transcript format. The
message JSON schema remains backward compatible while adding replies, edits,
device-local deletion, global tombstones, reactions, and mutation timestamps.
The existing platform emoji renderer and picker remain in place.

## Send-emphasis gesture

`SendEmphasisStateMachine` is the single source of truth for the interaction.
Its phases are `Idle`, `Pressed`, `Holding`, `Adjusting`, `AtMaximum`, `Sending`,
and `Cancelling`.

- A release before the 420 ms hold threshold sends a normal message.
- Holding grows a normalized progress value from 0 to 1 with an ease-out curve.
- Once horizontal adjustment begins, movement is direct: left subtracts progress
  and right adds it. One tokenized drag range maps to the full normalized range.
- Vertical movement beyond the cancellation token cancels without clearing the
  draft or creating a message.
- Maximum enters at 0.98 and rearms below 0.90. The visual preview keeps a subtle
  shake while maximum remains held; its haptic fires once per press gesture.
- App pause, pointer cancellation, empty text, attachment, and emoji actions all
  cancel an active gesture.

The fast-changing state is owned by `MessageInputBar`; the message list and its
`StateFlow` are not updated on gesture frames. Only the quantized result is sent.

## Stored emphasis

`Message.emphasisLevel` is an integer with a default of `0`. Levels are:

| Value | Meaning |
|---:|---|
| 0 | Normal |
| 1 | Low |
| 2 | Medium |
| 3 | High |
| 4 | Maximum |

Missing values decode to normal, invalid values are clamped at local/remote file
boundaries, and clients that ignore unknown JSON keys render the original plain
text. Copy, search, notifications, attachments, receipts, and retry behavior keep
using `Message.text` unchanged.

The visual cap depends on message length and is fixed from the press snapshot:
short messages can add 15 sp, medium messages 10 sp, longer messages 6 sp, and
very long messages 3 sp. Preview and sent bubbles use the same typography and
padding calculation, with real text layout values instead of only scaling glyphs.

A message containing exactly one ICU grapheme with an emoji property renders at
64 sp without a bubble, or 72 sp at maximum emphasis. This includes flags,
keycaps, modifiers, and ZWJ families. Multiple emoji use the standard bubble.

## Message mutations and merge rules

Each message carries optional `replyToMessageId`, `editedAt`, `deletedAt`, and
`deletedForDeviceIds`, plus `updatedAt` (defaulting to the original timestamp for
older records). Repository mutations are suspend operations serialized by a
mutex; transcript replacement runs on `Dispatchers.IO` and is published only
after the atomic temporary-file replacement completes.

- Content and reaction changes use the newest `updatedAt`.
- Receipt status, delivery time, and read time merge independently.
- Device-local deletion IDs are unioned and do not advance the content clock.
- A global `deletedAt` tombstone is monotonic. Normalization clears text,
  attachments, reactions, reply pointers, edit metadata, and emphasis, so a
  stale client cannot restore deleted content.
- Reply quotes retain only the target ID. A deleted target renders a deleted
  placeholder; a locally hidden or missing target renders as unavailable.
- Editing is limited to the sender's non-empty text or caption for 30 minutes,
  inclusive of the exact boundary. Global deletion remains available for the
  sender indefinitely.

Android peers reuse the existing full-message `chat.send` command for any changed
mutation fingerprint (`updatedAt`, tombstone, or local-delete set), including
already-delivered messages. This keeps edits, reactions, and deletions moving in
both directions without adding a relay endpoint.

Android owns selection and action controls. A stationary long press enters
multi-select without exposing the platform text-selection toolbar. Single-message
actions include reactions, Reply, eligible Edit, and Delete; multi-select exposes
Delete only. Delete confirmation offers device-local deletion, sender-only global
deletion when eligible, and Cancel.

Selection feedback changes only the bubble surface color; it does not add an
outline or check badge. Press indication is clipped to the grouped bubble shape.
Standard message text starts at 15 sp. Until an outgoing
message is read, its sent time and a single check remain visible. Read messages
hide receipt metadata by default; tapping one reveals its sent and read times.

## Gesture, list, and inset behavior

Message rows do not use lazy-list placement animation. Vertical movement remains
owned by `LazyColumn`; a direction-locked right drag moves only the active row,
arms near 56 dp, caps near 72 dp with resistance, and settles with a non-bouncy
spring. Only a newly sent local row receives an opacity, 10 dp translation, and
0.96-to-1 scale entrance with damping `0.65`.

New image messages carry optional pixel dimensions, reserve their full aspect
ratio, and render with `ContentScale.Fit` instead of cropping. Older records keep
working through the legacy `attachment` field. New messages also carry an ordered
`attachments` list and retain its first item in `attachment` as a compatibility
fallback. A shared 48 MiB LRU caches 960 px list/composer previews and is
invalidated through per-attachment revision flows, avoiding list-wide
recomposition and repeat decoding during flings.

The document picker has no app-side count limit. One selection creates one
message, caption, receipt, reply target, and mutation identity. A single image
uses an uncropped preview capped at 240 dp; multiple images use Material 3's
multi-browse carousel at the same height with 180 dp preferred items. Non-image
files remain cards in that message. Peer transfer walks the canonical list
sequentially and deduplicates attachment IDs.

The emoji panel and IME are mutually exclusive. The former upward composer
gesture has been removed; the keyboard opens only through normal text-field
focus. `adjustResize`, `imeNestedScroll`, and a single unioned layout-phase inset
padding remain active. The header consumes
status/cutout insets, while the composer gradient paints before navigation/IME
padding so the gesture area is never transparent.

On first layout, the conversation centers the earliest incoming unread message;
when none exists, it opens at the latest message. That target is chosen before
incoming receipts are marked read. The lazy-list state is owned above the
full-screen image transition, so closing the viewer returns to the same message
and offset. The viewer pages horizontally through images in the same message.
At 1× zoom, a direction-locked vertical drag tracks the finger and dismisses in
either direction after 96 dp or about 1,200 dp/s; a cancelled drag returns with a
critically damped spring. Zoomed images pan instead of dismissing. The close
action consumes status/cutout insets, and tapping the black area outside the
image also closes it.

## Theme and typography

`PrivateVaultTheme` provides complete stable light and dark Material 3 schemes.
Dynamic color remains opt-in because the supplied reference relies on a controlled
violet/indigo relationship. All chat colors come from semantic Material roles.
Chat-only spacing, shape, gesture, and spring values live in
`ChatExpressiveTokens`.

Compose Material 3 is pinned to `1.5.0-alpha23` for the official expressive
shape catalog and morph path API. Composer controls, the image carousel, delete
sheet, and debug keypad use official Material 3 components. The four debug-key
indicators choose distinct Material shapes per screen entry and morph on fill;
the numeric controls adapt from 64 to 104 dp based on available width and height.

The persisted DataStore preference supports `System`, `Light`, and `Dark`.
The chat header offers an immediate light/dark action and Settings exposes all
three states. System-bar icon appearance follows the active theme.

Google Sans Flex version 4.005 is bundled as a local variable TTF. Its `wght`,
`wdth`, `opsz`, `GRAD`, `ROND`, and `slnt` axes are retained. The theme supplies
several weight instances, uses conservative roundness, and lets Android font
fallback handle scripts absent from the Latin subset. The font is not downloaded
at runtime.

## Accessibility and reduced motion

- The send action describes both tap and hold behavior.
- The live preview exposes only semantic level changes, avoiding per-frame
  TalkBack announcements.
- Messages announce non-normal emphasis without modifying their text.
- Touch targets are at least 48 dp and composer actions remain visible at large
  font scales.
- When system animators are disabled, typing loops and overshoot are disabled;
  direct gesture adjustment and static color/ring feedback remain.
- Foreground loss cancels an active send gesture without deleting the draft.

## Performance and measurement

- Gesture progress is local to the composer/preview subtree.
- The message `LazyColumn` uses stable IDs and attachment/text content types.
- Historical rows have no placement spring, animation clock, or geometry callback.
- Context menus are composed and positioned only for the selected anchor row;
  reply lookup is indexed and ICU single-emoji results are cached.
- Automatic bottom following performs at most one list scroll per state change;
  image decode never drives repeated scroll calls.
- Attachment decode is sampled to the requested preview size and cached.
- Debug builds log JankStats frames under `ChatFrameTiming`.
- A source baseline profile covers app startup, theme, chat list, bubbles, and the
  composer.

The build and automated tests verify behavior and integration. Sustained 60 FPS
is **not certified** by this repository run: Android's own guidance warns that
emulator benchmark figures are not representative. Validate JankStats/Perfetto
on a mid-range physical device before treating the performance acceptance item as
complete.

## Tests

Unit tests cover message JSON backward compatibility, mutation merge order,
tombstone resurrection prevention, receipt independence, edit ownership and the
30-minute boundary, local/global/mixed deletion, reply references, emoji grapheme
classification, and maximum-emphasis lifecycle. Compose instrumentation covers
app-owned long-press selection, selected semantics, multi-select action filtering,
and Android ICU emoji sequences. Web tests cover tombstone reconnect behavior,
local-delete union, and emoji grapheme rendering.

## Licensing

The font was obtained from the official Google Fonts CSS service after the Google
Fonts metadata API reported `license: ofl` and `isOpenSource: true`. The official
Latin variable WOFF2 was converted losslessly to TTF for Android resource loading;
its variation tables and font metadata were retained.

- Font copyright: Copyright 2015 Google LLC. All Rights Reserved.
- Font version: 4.005
- SHA-256: `61F3E12855E7D20D97737E749501973E1C62DED3F8CFB1EBB467DB184AB1BEE0`
- License: SIL Open Font License 1.1
- Full license resource: `res/raw/google_sans_flex_ofl.txt`
- License UI: Settings → Open-source licenses
