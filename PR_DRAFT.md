## Problem

The old Discord RPC was built on top of Discord's proprietary Social SDK AAR — a 29.6MB binary blob with a C++ native bridge that needed CMake, NDK, and flavor-specific stubs. It was a pain to maintain, bloated the APK, and the SDK itself is pretty much deprecated/unmaintained by Discord.

## Cause

The vendor SDK was the only integration point. It pulled in native code, forced flavor gating (only GMS builds), and made it impossible to iterate quickly on RPC features.

## Solution

Ripped all of that out and built a pure-Kotlin replacement from scratch:

- **OAuth2 PKCE flow** via a local loopback HTTP server (Ktor CIO on `127.0.0.1`) — no custom URI schemes, no `AuthenticationActivity` in the manifest
- **Gateway WebSocket** (OkHttp) with full identify/resume/heartbeat lifecycle, exponential backoff, rate-limit handling, and stale-connection guards
- **Encrypted token store** using AES-GCM + Android KeyStore (replaced `EncryptedSharedPreferences`)
- **Unified state derivation** via `syncDiscordState()` so playback state doesn't glitch regardless of what the user does
- **Activity template rendering** so placeholders like `{song.name}` actually resolve
- **Debug logging** (Timber) across the whole subsystem for field debuggability
- **Unit tests** for the token store and loopback auth server

Follow-up commits cleaned up stale sessions, fixed reconnection races, added user-info refresh on gateway READY, reduced detection signals (browser fingerprint, stable device IDs, rate-limit respect), and more.

## Testing

Built with `:app:assembleFossDebug` — all three flavors (foss, gms, izzy) now share the same Discord code since there's no native dependency. Tested OAuth authorization, token refresh, reconnection, presence updates, settings toggles, and screen-off timeout behavior.

## Related Issues

- Closes (Discord RPC rewrite — replaces proprietary SDK with open, pure-Kotlin implementation)
