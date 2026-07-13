# Expressive chat implementation

## Scope

This change upgrades the native Compose chat UI without changing relay routing,
encryption, attachment storage, the local line-delimited transcript, or delivery
receipts. Animated emoji support is intentionally excluded at the user's request;
the existing platform emoji renderer and picker remain in place.

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
- Maximum enters at 0.98 and rearms below 0.90. The pop counter increments only
  on a valid threshold crossing, which prevents repeated haptics and animation.
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

## Theme and typography

`PrivateVaultTheme` provides complete stable light and dark Material 3 schemes.
Dynamic color remains opt-in because the supplied reference relies on a controlled
violet/indigo relationship. All chat colors come from semantic Material roles.
Chat-only spacing, shape, gesture, and spring values live in
`ChatExpressiveTokens`.

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
  direct gesture adjustment and static color/weight feedback remain.
- Foreground loss cancels an active send gesture without deleting the draft.

## Performance and measurement

- Gesture progress is local to the composer/preview subtree.
- The message `LazyColumn` uses stable message IDs.
- Offscreen work added by this feature is zero; no animation clock is attached to
  historical messages.
- Debug builds log JankStats frames under `ChatFrameTiming`.
- A source baseline profile covers app startup, theme, chat list, bubbles, and the
  composer.

The build and automated tests verify behavior and integration. Sustained 60 FPS
is **not certified** by this repository run: Android's own guidance warns that
emulator benchmark figures are not representative. Validate JankStats/Perfetto
on a mid-range physical device before treating the performance acceptance item as
complete.

## Tests

Unit tests cover clamping, nonlinear hold timing, drag mapping, full-left reset,
maximum hysteresis, cancellation, quantization, length caps, message JSON backward
compatibility, and theme-value parsing. Compose instrumentation tests cover normal
send, held emphasis, full-left reset, vertical cancellation, disabled/accessible
send semantics, light/dark rendering with preserved draft state, large font scale,
and DataStore persistence.

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
