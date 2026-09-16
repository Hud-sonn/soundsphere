# Premium Tier Scaffold — Investigation & Implementation Record

Date: 2026-09-14
Status: Local (NOT pushed)
Why: Scaffold premium tier infrastructure before payment integration — tier checking + cap enforcement now, OPay payment later.

## What was built

### New file: `backend-auth/routers/billing.py`

Server-side tier checking and cap enforcement module. **Read-only** — no payment logic.

**Security model:**
- Tier determined SOLELY by `subscriptions` table (`service_role` only, no `authenticated` UPDATE policy)
- Client never sends tier info
- No caching — every cap check queries fresh, so expiration is instant
- `user_settings.settings` CHECK prevents `is_pro`/`plan` keys
- `prevent_privileged_user_column_changes` trigger protects `users.role`
- Payment integration (OPay/Stripe) will write to `subscriptions` via `service_role` webhooks only

**Functions:**
- `get_user_tier(db, user_id)` → `'premium'` or `'free'`
- `get_cap(db, user_id, cap_name)` → `int | None` (None = unlimited)
- `check_cap(db, user_id, cap_name, current_count)` → raises 409 if hit
- `check_blend_creation_cap(db, user_id)` → Blend ownership cap

**Cap definitions:**

| Cap | Free | Premium | Table |
|-----|------|---------|-------|
| `playlists` | 20 | unlimited | `playlists` |
| `liked_tracks` | 2000 | unlimited | `liked_tracks` |
| `followed_artists` | 200 | unlimited | `followed_artists` |
| `playlist_tracks` | 500 | 1000 | `playlist_tracks` |
| `history_rows` | 500 | unlimited | `history` |
| `ai_daily` | 2 | 10 | `ai_generation_usage` |
| `blends_owned` | 3 | 10 | `playlists` (is_collaborative) |

### Modified: `backend-auth/routers/user.py`

- Import `get_cap`, `check_cap`, `check_blend_creation_cap` from billing module
- All 5 cap checks replaced with tier-aware `check_cap()` calls
- `create_playlist` gains Blend ownership cap (3 free, 10 premium)
- `_prune_history` is now tier-aware (skips pruning for unlimited)
- Old `_PLAYLIST_SYNC_LIMIT`, `_LIKED_LIMIT`, etc. constants removed

### Modified: `backend-auth/routers/ai.py`

- Import `get_cap` from billing module
- `_DAILY_GENERATION_LIMIT` replaced with `get_cap(db, user_id, "ai_daily")`
- Premium users get 10/day instead of 2

## Expiration handling

When `subscriptions.expires_at` passes:
1. `get_user_tier()` returns `'free'` (checked via `expires_at > now()`)
2. User automatically drops to free tier on next request
3. No cron job needed — inline check, instant transition
4. All user data preserved (grandfathered) — they just hit free caps again

## What's NOT here (by design)

- No payment code, no OPay/Stripe integration
- No UI changes (app handles 409s gracefully already)
- No new tables (uses existing `subscriptions` table)
- No `routers/billing.py` mounted in `main.py` (it's a helper module, not a router)

## Subscriptions table schema (existing, created via MCP migration)

```sql
subscriptions (
  user_id    uuid PK FK → users.id,
  tier       text DEFAULT 'free',
  is_pro     boolean DEFAULT false,
  expires_at timestamptz,
  updated_at timestamptz DEFAULT now()
)
```

RLS enabled. No `authenticated` UPDATE policy — only `service_role` writes (via webhook).

## Security verification

- [x] `subscriptions` table: RLS enabled, no `authenticated` UPDATE policy
- [x] `user_settings.settings`: CHECK constraint prevents `is_pro`, `plan`, `ai_generations_remaining` keys
- [x] `users.role`: protected by `prevent_privileged_user_column_changes` trigger
- [x] `playlists.is_collaborative`: protected by `prevent_privileged_playlist_column_changes` trigger
- [x] No client-sent tier information accepted anywhere in the backend
- [x] No caching of tier status — fresh DB query on every cap check
- [x] Error path defaults to `'free'` (fail-closed)
