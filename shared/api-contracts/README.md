# API Contracts

Shared TypeScript types for the POC backend and web portal. The Android app mirrors
these models with Kotlin serializable data classes.

Message mutation fields are optional in TypeScript and defaulted in Kotlin so old
browser storage and newline-delimited Android transcripts remain readable.
`updatedAt` orders content/reaction mutations, receipt state merges independently,
`deletedForDeviceIds` is unioned, and `deletedAt` is an irreversible global
tombstone. Attachment `width` and `height` are optional. `attachments` is an
ordered multi-file list, while legacy `attachment` remains the first-item
fallback. Clients canonicalize and deduplicate both fields, clear both on global
deletion, and transfer every canonical attachment sequentially. The relay remains
transport-only; no new endpoint is required.
