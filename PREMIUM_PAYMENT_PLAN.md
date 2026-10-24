Date: 2026-10-11
Status: Live — premium split decided, flags applied 2026-10-11, backend caps fully enforced
Why: user asked to keep the premium/payment discussion + remaining work + session-lockout findings in one note

Changelog:
- 2026-10-11: corrected — blend cap IS enforced (`check_blend_creation_cap`, user.py:383); no backend cap gap. Flag wiring B1–B6 applied to app (uncompiled).
- 2026-10-11: items 4/6/7 DONE. Streak card on Stats top (app local). Session fix shipped: `POST /auth/refresh` live on prod (verified 401-no-auth = new code serving); app auto-renews <48h via `refreshSessionIfExpiring` at startup (local). Double-submit guard live: 90s create dedupe (verified boot-test; prod dupes `alpha 2`/`Slayr` predate it).

# Premium & Payment — status, split, remaining work

## Premium split (decided by user from recommendation menu)

Premium = **raised caps + 2 visible extras**:
- **Pro (gated):** Smart Shuffle, per-output EQ routes
- **Free:** streak, library health, playlist folders, Wrapped share, cross-device week, uniqueness, LT vote-skip, skip count
- Server caps (already tier-aware in `billing.py:_CAPS`): playlists 20/∞, liked 2000/∞, follows 200/∞, tracks/playlist 500/1000, history 500/∞, AI/day 2/10, blends-owned 3/10

## Premium flag wiring (APPLIED 2026-10-11, uncompiled)

| # | File | Edit | State |
|---|---|---|---|
| 1 | `SyncRepository.kt` | `_isPremium` StateFlow + `refreshPremiumStatus()` via `SyncService.getSubscriptionStatus` | applied |
| 2 | `MainActivity.kt` onCreate | `syncRepository.refreshPremiumStatus()` at startup | applied |
| 3 | `PaymentViewModel.kt` | injects SyncRepository, refreshes global flag after verify success | applied |
| 4 | `MusicService.kt:2735` | behavior gate: `SmartShuffleKey && isPremium` | applied |
| 5 | `PlayerSettings.kt:968` | free → lock + navigate `"payment"`; premium → Switch | applied |
| 6 | `EqScreen.kt` RouteEqSection | free → locked card + Go Premium CTA; premium → rows | applied |

Backend caps: **fully enforced, no edits needed** — verified `check_blend_creation_cap` called from `create_playlist` (`user.py:383`); all other caps wired (`user.py` liked/playlists/tracks/history/follows, `ai.py` quota).

## Remaining — PAYMENT

1. Render env (user): `PAYSTACK_SECRET_KEY`, `PAYSTACK_PLANS_JSON` (kobo), `PAYMENT_CALLBACK_URL` — `/initialize` 503s until set
2. Paystack dashboard (user): create plan (e.g. `monthly-pro` ₦700), register webhook `/paystack/webhook`
3. Real Get Premium screen (benefits mirroring `_CAPS`), entry points: Account row, cap-409→paywall, Home promo card (X-dismiss remembered, hidden for premium), sidebar hidden for subscribers
4. E2E test with `4082 4082 4082 4082`, then live key

## Remaining — PREM features

- Streak → Stats top (decided, unbuilt); skip-count surface undecided
- Paywall screen (biggest chunk); uncompiled pile (payment UI, library health, folders) needs constrained build
- CHANGES.md + tests + commit on user go

## Recommended order

`user.py` blend cap → flag wiring 1–3 → paywall + entries → gates 4–6 → build/test pile → env vars → E2E (blend cap verified enforced — skip to flags)

# Session lockouts — root cause

## How long sessions last

- **7 days**: `JWT_EXPIRE_MINUTES=10080` in `soundsphere-auth.env`; `auth/jwt.py:create_token` puts `exp = now + 10080min`
- OTP codes: 10 min (`OTP_VALID_SECONDS=600`); login rate limit 10/hour

## Why users get "locked out quick"

**No refresh-token flow exists.** The JWT is the only credential; once it dies (or any 401 happens) the app *deletes* it and the user must log in again manually:

- `SyncRepository.handleFailure` (`SyncRepository.kt:~866`): any 401 during background sync → `authRepository.clearToken()`. Token expiry hits mid-background-sync, so the user opens the app already logged out with no message.
- Same pattern in `AuthViewModel` (profile update 401s → clearToken).
- Aggravator — dual-service failover: if old + new backends ever run with **different `JWT_SECRET`**, every fallback request 401s → mass instant logout. Any two-service setup MUST share the secret.
- Re-entry friction: `/login` capped at 10/hour, so logged-out users who mistype get temporarily locked out of returning.

## Fix options (not applied)

1. Silent refresh: `/auth/refresh` endpoint minting a new token from a still-valid one (or long-lived refresh tokens), called pre-expiry by a WorkManager/periodic sync task.
2. Cheaper now: don't silently wipe the session on background-sync 401 — set a `sessionExpired` flag and prompt re-login in-UI, keeping the email prefilled.
3. Raise `JWT_EXPIRE_MINUTES` (e.g. 30 days) — papering over, but lowers lockout frequency without code changes.
