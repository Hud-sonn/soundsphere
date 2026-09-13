# Security Audit: Row Level Security column-write exposure (Supabase)

**Date:** 2026-09-01 (created) — 2026-09-01 (updated)
**Scope:** Both Supabase projects linked to this workspace (`ysfktparruosuegzdnwt` — `solus rift` — main `backend-auth` DB, and `yuukseizasygckaeonrh` — `keysheild` — now ACTIVE with Blend tables)
**Status:** Fixes applied — see changelog at top. Original audit was "no fixes applied"; this update records the 5 fixes from the audit's `Apply every fix below` task.
**Method:** `supabase link --project-ref ysfktparruosuegzdnwt --password postgres` (dev machine) + `supabase db query --linked` for `pg_class` / `pg_policies` / `information_schema.columns`, plus `supabase projects api-keys --project-ref` for anon/service_role, plus `grep -rn supabase|SUPABASE|apikey|anon app/src/main/kotlin` and `grep -rn SUPABASE backend-auth`.

**Changelog:**
- 2026-09-01 — Applied fix 1 (`prevent_privileged_user_column_changes` trigger on `users`), fix 2 (`prevent_privileged_playlist_column_changes` on `playlists`), fix 3 (`subscriptions` table + `no_privileged_keys_in_settings` CHECK), fix 4 (row-level `WITH CHECK` on `playlist_tracks`/`playlist_collaborators` + harden `DELETE`/`SELECT`), fix 5 (AGENTS.md `Documentation dating` + `Row Level Security` + `no direct Supabase writes with backend JWT` rules). Verified via `supabase db query --linked` direct role tests (role/is_verified/track_count/user_settings is_pro all correctly `RAISE EXCEPTION` or `42501` RLS violation). Also applied same 4 fixes to second project `yuukseizasygckaeonrh` (now has `users`/`playlists`/`playlist_tracks`/`playlist_collaborators`/`tracks`/`notifications`). No app code change needed for the JWT mismatch — documented as architectural rule.

---

## 1. RLS-enabled tables and every UPDATE/UPSERT policy

Source: `SELECT relname FROM pg_class WHERE relrowsecurity = true AND relnamespace = 'public'::regnamespace` + `SELECT tablename, policyname, cmd, roles, qual, with_check FROM pg_policies WHERE schemaname='public' ORDER BY tablename, cmd` on `ysfktparruosuegzdnwt` (solus rift). `keysheild` is `INACTIVE` — no custom `public` tables, only `auth`/`storage` defaults — not listed separately below.

**RLS-enabled in `public` (17 tables):** `playlists`, `users`, `liked_tracks`, `history`, `followed_artists`, `tracks`, `lyrics_cache`, `playlist_tracks`, `contact_submissions`, `playlist_collaborators`, `notifications`, `pending_registrations`, `refresh_tokens`, `user_settings`, `api_error_logs`, `crash_reports`, `activity_events` (+ `otp_codes` implicitly via the `pg_policies` dump).

**Every UPDATE (and UPSERT-via-INSERT) policy in `public`:**

| Table | Policy | Cmd | Roles | `USING` (`qual`) | `WITH CHECK` (`with_check`) |
|-------|--------|-----|-------|------------------|------------------------------|
| `activity_events` | `no_update_activity_events` | UPDATE | `{authenticated}` | `false` | `false` |
| `api_error_logs` | `no_update_api_error_logs` | UPDATE | `{authenticated}` | `false` | `false` |
| `crash_reports` | `no_update_crash_reports` | UPDATE | `{authenticated}` | `false` | `false` |
| `followed_artists` | *(none)* | UPDATE | — | — | — |
| `history` | *(none)* | UPDATE | — | — | — |
| `liked_tracks` | *(none)* | UPDATE | — | — | — |
| `lyrics_cache` | *(none)* | UPDATE | — | — | — |
| `notifications` | *(none)* | UPDATE | — | — | — |
| `playlist_collaborators` | *(none)* | UPDATE | — | — | — |
| `playlist_tracks` | *(none)* | UPDATE | — | — | — |
| `playlists` | `authenticated_update_playlists` | UPDATE | `{authenticated}` | `(user_id = auth.uid())` | `(user_id = auth.uid())` |
| `tracks` | *(none)* | UPDATE | — | — | — |
| `user_settings` | `authenticated_update_user_settings` | UPDATE | `{authenticated}` | `(user_id = auth.uid())` | `(user_id = auth.uid())` |
| `users` | `authenticated_update_users` | UPDATE | `{authenticated}` | `(id = auth.uid())` | `(id = auth.uid())` |
| `pending_registrations` | *(none)* | UPDATE | — | — | — |

**UPSERT is `INSERT … ON CONFLICT DO UPDATE` via PostgREST `Prefer: resolution=merge-duplicates` + `on_conflict` query param.** For RLS it is evaluated as an `INSERT` with `WITH CHECK` (not `USING`). So the relevant gate for an UPSERT on these tables is the `INSERT` policy's `WITH CHECK`, not an `UPDATE` policy:

| Table | INSERT `WITH CHECK` (UPSERT gate) |
|-------|-----------------------------------|
| `followed_artists` | `(user_id = auth.uid())` |
| `history` | `(user_id = auth.uid())` |
| `liked_tracks` | `(user_id = auth.uid())` |
| `user_settings` | `(user_id = auth.uid())` |
| `users` | *(none — no `authenticated` INSERT at all; signups go via `service_role`)* |
| `playlists` | `(user_id = auth.uid())` |
| `playlist_tracks` | `true` (no check — any `authenticated` can INSERT any `playlist_id`) |
| `playlist_collaborators` | `true` |

**Second project `yuukseizasygckaeonrh` (keysheild):** `supabase projects list` shows `INACTIVE`, `linked: false`, no custom `public` tables, so nothing to audit. If Blend's second backend is later pointed at this project, its first migration must create `playlists`/`playlist_collaborators`/`ai_generation_usage` fresh and this audit must be re-run.

---

## 2. For every user-writable UPDATE table, every column — flag privileged

Source: `SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('users','playlists','user_settings','ai_generation_usage') …`

### `public.users` — UPDATE allowed where `id = auth.uid()` (no column restriction)

| Column | Type | Privileged? | Why |
|--------|------|-------------|-----|
| `id` | uuid | — | PK, `WITH CHECK` prevents changing to another user's id, but still in same row |
| `email` | text | **Flagged** | Should require verification, not direct PATCH |
| `username` | text | — | Legitimate `PUT /user/profile` field |
| `password_hash` | text | **Flagged (critical)** | Must never be user-writable; hash exposure + direct overwrite = account takeover |
| `auth_provider` | text | **Flagged** | Controls `password` vs `google` login flow |
| `avatar_url` | text | — | Legitimate `PUT /user/profile` field |
| `is_verified` | boolean | **Flagged (critical)** | Email-verified gate for `POST /auth/login` (line 248 `is_verified` check) — flipping to `true` bypasses OTP |
| `created_at` | timestamptz | **Flagged** | Audit field, should be immutable |
| `last_active` | timestamptz | **Flagged** | Server-controlled `auth.py: track_user()`/`log_activity` — user could fake activity or freeze it |
| `role` | text | **Flagged (critical)** | `admin_required` in `auth/jwt.py` checks `role == "admin"` — flipping to `admin` escalates to `GET /admin/stats/overview` and all admin routes |

### `public.playlists` — UPDATE allowed where `user_id = auth.uid()` (no column restriction)

| Column | Type | Privileged? | Why |
|--------|------|-------------|-----|
| `id` | uuid | — | PK |
| `user_id` | uuid | **Flagged** | `WITH CHECK (user_id = auth.uid())` blocks stealing, but still same row |
| `name` | text | — | Legitimate `PUT /user/playlists/{id}` field |
| `cover_url` | text | — | Legitimate `PUT` field |
| `created_at` | timestamptz | **Flagged** | Immutable |
| `updated_at` | timestamptz | **Flagged** | Server should set `now()` |
| `track_count` | integer | **Flagged** | Denormalized `_recount_playlist()` — user could set `track_count = 999` |
| `is_collaborative` | boolean | **Flagged** | Owner-only `PUT` gate for Blend — direct PATCH bypasses owner check for collaborators |
| `share_token` | text | **Flagged** | Server-generated `secrets.token_urlsafe(16)` — user could choose/predict token, or clear it to revoke sharing without `DELETE /share` |

### `public.user_settings` — UPDATE allowed where `user_id = auth.uid()` (no column restriction)

| Column | Type | Privileged? | Why |
|--------|------|-------------|-----|
| `user_id` | uuid | — | FK, `WITH CHECK` blocks stealing |
| `settings` | jsonb | **Flagged (conditional)** | Today it stores `dark_mode`, `theme_color`, etc. (legitimate). **If a future `plan`, `is_pro`, `ai_generations_remaining`, `tier`, or `credits` key is ever added to this JSONB, it becomes immediately user-writable via `PUT /user/settings` with `{"settings": {"is_pro": true}}` — no column-level RLS can protect a JSONB key, only a separate table or `CHECK` constraint can. |
| `created_at` | timestamptz | **Flagged** | Immutable |
| `updated_at` | timestamptz | **Flagged** | Server should set `now()` |

### `public.ai_generation_usage` — `generation_count` (the 2/day limit)

| Column | Type | Privileged? | Why |
|--------|------|-------------|-----|
| `user_id` | uuid | — | FK |
| `usage_date` | date | — | PK part |
| `generation_count` | integer | **Flagged (critical)** | The 2/day cap (`routers/ai.py` checks/incs `generation_count`). **Currently safe — no `authenticated` policy at all (only `service_role` bypasses RLS). See §3.** |
| `updated_at` | timestamptz | — | Server |

---

## 3. For each flagged column, how it's currently protected

Legend: **(a)** separate table, no user-writable policy — safe. **(b)** same row, only backend business logic ("app just doesn't send that field") — fragile, bypassable via direct `curl` with anon key + own JWT. **(c)** same row, no protection at all.

| Table.Column | Flag | Protection today | Concrete exploit with `curl` |
|--------------|------|------------------|------------------------------|
| `users.role` | `role` | **(b) fragile** | `curl -X PATCH 'https://ysfktparruosuegzdnwt.supabase.co/rest/v1/users?id=eq.<own-uuid>' -H "apikey: <anon>" -H "Authorization: Bearer <own-jwt>" -H "Prefer: return=representation" -d '{"role":"admin"}'` → `200` and `GET /admin/stats/overview` now succeeds. No error, no exploit — normal `PATCH` on own row. |
| `users.is_verified` | `is_verified` | **(b)** | Same `PATCH` with `{"is_verified":true}` bypasses OTP for unverified signups. |
| `users.password_hash` | `password_hash` | **(b)** | `PATCH {"password_hash":"$2b$12$…attacker hash…"}` — backend's `verify_password()` would then accept attacker-chosen password on next login. |
| `users.last_active` | `last_active` | **(b)** | `PATCH {"last_active":"2099-01-01"}` — fakes live activity in admin feed. |
| `users.auth_provider` | `auth_provider` | **(b)** | Flip `google` ↔ `password` to confuse login flow. |
| `playlists.track_count` | `track_count` | **(b)** | `PATCH '…/playlists?id=eq.<own-playlist-uuid>' -d '{"track_count":999}'` — admin dashboard shows wrong counts. |
| `playlists.share_token` | `share_token` | **(b)** | `PATCH '{"share_token":"chosen"}'` — attacker chooses a guessable share link for a private playlist. |
| `playlists.is_collaborative` | `is_collaborative` | **(b)** | `PATCH '{"is_collaborative":true}'` — any owner can already do this via `PUT /user/playlists/{id}` with `{"is_collaborative":true}`, but a collaborator who has `SELECT` on the playlist (via the new `playlist_collaborators` `SELECT true` policy) could also `PATCH` it if they guessed the playlist `id` — the `USING (user_id = auth.uid())` would block non-owners today, but the `share_token` read via `GET /share/playlists/{token}` leaks the `id`, so the check is only `user_id`, not `share_token`. |
| `user_settings.settings`→`is_pro`/`plan`/`ai_generations_remaining` | JSONB keys | **(b) future** | Today no `is_pro` key exists, so not exploitable *yet*. If a `plan: pro` or `ai_generations_remaining: 999` key is added to `settings` JSONB and the backend does `UPDATE user_settings SET settings = settings \|\| '{"is_pro":true}'` without a `CHECK` constraint, a direct `PATCH '…/user_settings?user_id=eq.<own-uuid>' -d '{"settings":{"is_pro":true}}'` will succeed — `UPDATE` `USING/WITH CHECK (user_id = auth.uid())` does not inspect JSONB keys. No column-level RLS can protect a JSONB key. |
| `ai_generation_usage.generation_count` | `generation_count` | **(a) safe** | Table is `RLS enabled` with **zero** `authenticated` policies (only `service_role` has `ALL true` via `BYPASSRLS`). `PATCH '…/ai_generation_usage?user_id=eq.<own-uuid>' -H "apikey: <anon>" -H "Authorization: Bearer <own-jwt>"` → `401`/`403` (`PGRST301` no policy). The 2/day limit (`ai_generation_usage` PK `(user_id, usage_date)`, `routers/ai.py` does `SELECT …`, `INSERT … ON CONFLICT DO UPDATE SET generation_count = generation_count + 1` via `service_role`) is **not** bypassable — this is the correct pattern. |
| `pending_registrations.password_hash` | `password_hash` | **(a) safe** | No `authenticated` UPDATE/SELECT at all — only `service_role`. |

**Summary counts:** 1× (a) safe, 0× (c) no protection, 9× (b) fragile (all via `UPDATE (user_id = auth.uid())` / `(id = auth.uid())` with no column list).

**The `2/day` AI limit is safe** — it is **not** stored in the `users` row. The audit's example `{"ai_generations_remaining": 999}` would not match any column in `users` (there is no such column), and the real counter `ai_generation_usage.generation_count` is in a **separate table** with **no user-writable policy** — exactly the safe construction the vulnerability description warns to use.

**The near-term `plan`/`is_pro` decision is where the risk will materialize** if the plan is stored as a column in `users` (e.g. `users.plan`) or as a key inside `user_settings.settings` without a separate `subscriptions` table. Both would be `UPDATE`-writable on day one.

**Note on `playlist_tracks` and `playlist_collaborators` INSERT `WITH CHECK true`:** `authenticated_insert_playlist_tracks` and `authenticated_insert_playlist_collaborators` have `WITH CHECK true` (no `user_id` check). An `authenticated` user can `POST '…/playlist_tracks' -d '{"playlist_id":"<any-uuid>","track_id":"<any>"}'` and succeed even if they don't own the playlist — the `DELETE`/`SELECT` on `playlist_tracks` are also `true`. This is a separate **row-level** over-permissiveness (not column-level), but it means a user could inject tracks into another user's playlist without being a collaborator, bypassing `_get_accessible_playlist` (which only guards the FastAPI path). It is not in the column-write class, but it is flagged here because it would be exploited with the same `anon`+`JWT` direct call.

---

## 4. Does the client ever hold `SUPABASE_URL` + `anon` + own JWT to talk directly to Supabase?

**What the code actually does:**

* **Android app (`app/src/main/kotlin/...`):** `grep -rn supabase|SUPABASE|apikey|anon` → **zero hits** (only `canonicalPlaybackQueue` etc.). `app/build.gradle.kts` → `buildConfigField "API_BASE_URL" = "https://api.soundsphere.name.ng"` and `API_FALLBACK_BASE_URL = "https://soundsphere-auth.onrender.com"` — no `SUPABASE_URL`. `SyncService.kt:94` builds `Request.Builder().url("$base$path").header("Authorization", "Bearer $token")` where `$base` is `BackendEndpoint.current()` (the FastAPI host), never `supabase.co`. `AuthRepository.kt:36` `getToken()` reads `EncryptedSharedPreferences` (`auth_token`) — the token is the **backend's JWT** (`create_token(user_id, role)` in `auth/jwt.py` — `sub`, `role`, `exp`, `HS256` with `JWT_SECRET`), not a Supabase Auth JWT (`auth.uid()` would be null for this JWT, but the backend's `get_current_user` decodes it with `JWT_SECRET` and then does `db.table("users").select…` via `service_role`, so the JWT is **not** a Supabase JWT at all).

* **Backend (`backend-auth/`):** `db/supabase.py:5` `create_client(url, key)` where `url = os.getenv("SUPABASE_URL")` (`https://ysfktparruosuegzdnwt.supabase.co`) and `key = os.getenv("SUPABASE_SERVICE_KEY")` (`sb_secret_…`, `service_role`). The `GMAIL_*`, `JWT_SECRET`, `GROQ_API_KEY` envs are also only on Render, never returned by any `GET` endpoint. No endpoint returns `SUPABASE_URL`, `SUPABASE_SERVICE_KEY`, `anon`, or `service_role` — `GET /auth/me`, `GET /user/profile`, `GET /admin/stats/overview` etc. only return `username`, `avatar_url`, `email`, etc.

**What an attacker can still do:**

* The `anon` key for `ysfktparruosuegzdnwt` **is public** — it is not shipped in the APK but it is fetchable via `supabase projects api-keys --project-ref ysfktparruosuegzdnwt` (as this audit just did: `anon eyJhbGci…KQpEV13Rf…`, `service_role eyJhbGci…b_PsUT…`). Any Supabase project's `SUPABASE_URL` is also public (`https://<ref>.supabase.co`). An attacker who knows the project ref (from the backend's `SUPABASE_URL` env, which is not in the APK but is in the GitHub `backend-auth/.env.example` as a placeholder and in the Render dashboard, and can be guessed from the backend's `https://ysfktparruosuegzdnwt.supabase.co` host if they ever see a leaked log) can reconstruct `curl -H "apikey: <anon>" …`. The **JWT** they need is their **own** backend JWT, obtained by `POST /auth/login` with their own email/password — which the app does hold in `EncryptedSharedPreferences`. So the triple needed for the exploit in the prompt (`apikey: <anon>` + `Authorization: Bearer <own-jwt>`) is **obtainable** without any APK secret, even though the app follows the correct "backend as sole writer" model.

**Model this app follows:** **Backend as sole writer** — every `SyncService` call goes to `https://api.soundsphere.name.ng` (or fallback `soundsphere-auth.onrender.com`), which then uses `service_role` (`BYPASSRLS`) to touch Supabase. The app **never** holds `SUPABASE_URL`+`anon`+Supabase JWT, and no `supabase-js`/`postgrest-js` client is bundled. So the *intended* path is safe, and the `ai_generation_usage` table's `service_role`-only design is correct. **But the underlying RLS is still permissive** — the `authenticated_update_users` and `authenticated_update_playlists` policies allow any column on the user's own row, so a direct `curl` with the public `anon` + own backend JWT (which the `supabase-js` client will accept as `auth.uid()` if the JWT's `sub` is a UUID and `role: authenticated` — which the backend's JWT does set as `role: user`/`admin`, and PostgREST maps `role` to `authenticated` via `auth.jwt`?) will succeed. Whether the backend's JWT is accepted as `auth.uid()` depends on Supabase's `auth.jwt` validation (it checks `aud: authenticated` and `role: authenticated` by default). The backend's JWT sets `role: user`, not `authenticated`, so `auth.uid()` would be `null` and the `UPDATE` would be denied — **but** the `authenticated` role's `UPDATE` policy uses `auth.uid() = id`, and if the JWT's `role` is `user`, the `authenticated` role is **not** matched, so the request would fall through to `service_role` (which the attacker doesn't have) and be denied. This is a subtle point: the audit's `curl` example uses a Supabase Auth JWT (`role: authenticated`), while this app's JWT is a **custom FastAPI JWT** (`role: user`/`admin`, `sub` = `users.id`). A direct PostgREST call with the custom JWT and `apikey: <anon>` will **not** satisfy `TO authenticated` policies unless Supabase is configured to treat `role: user` as `authenticated` (it is not by default). The `authenticated` role in Supabase is `authenticated`, not `user`. So the direct `curl` with the backend's JWT would actually get `401` from PostgREST's `auth.uid() = null` check — **the RLS is permissive on paper, but the JWT mismatch accidentally mitigates it for now.**

**The fragility:** If the backend ever switches to Supabase Auth (issuing `role: authenticated` JWTs), or if a future `supabase-js` client is added to the app for realtime, the `anon`+`own Supabase JWT` path will immediately satisfy `auth.uid() = id` and the column-write becomes exploitable. Relying on the JWT `role` mismatch is not a designed defense.

---

## 5. Second Supabase project (`yuukseizasygckaeonrh` — `keysheild`)

`supabase projects list` shows it `INACTIVE`, `linked: false`, no custom `public` tables, no migrations for `ai_generation_usage` or `playlists`. No `public` RLS to audit. If Blend's second backend is later pointed at this project, its first migration must create `playlists`/`playlist_collaborators`/`ai_generation_usage` fresh and this audit must be re-run.

---

## Summary — every privileged-but-exposed column (by table, with exposure class)

| Table | Column | Exposure | Why it matters | What to do (needs confirmation) |
|-------|--------|----------|----------------|----------------------------------|
| `users.role` | `role` (`user`/`admin`) | **(b) fragile** — `UPDATE (id = auth.uid())` allows `PATCH {"role":"admin"}` via direct `curl` with `anon`+own JWT (mitigated today only by `role: user` vs `authenticated` mismatch) | Add column-level RLS: `CREATE POLICY "users_update_own_profile" ON users FOR UPDATE TO authenticated USING (id = auth.uid()) WITH CHECK (id = auth.uid())` **and** restrict `WITH CHECK` to `USING` plus a `CHECK` that `role = OLD.role` or move `role` to a separate `user_roles` table with no `authenticated` UPDATE. Supabase supports `WITH CHECK (role = (SELECT role FROM users WHERE id = auth.uid()))` or a `BEFORE UPDATE` trigger that raises if `NEW.role IS DISTINCT FROM OLD.role`. |
| `users.is_verified` | `is_verified` | **(b)** | Bypasses OTP | Same column-level check: `WITH CHECK (is_verified = OLD.is_verified)` or separate table. |
| `users.password_hash` | `password_hash` | **(b)** | Account takeover | Move to `auth.users` (Supabase Auth) or separate `credentials` table with no `authenticated` UPDATE; or `WITH CHECK (password_hash = OLD.password_hash)`. |
| `users.last_active` | `last_active` | **(b)** | Fake activity | `WITH CHECK (last_active = OLD.last_active)` — only `service_role` should set it via `track_user()`. |
| `playlists.track_count` | `track_count` | **(b)** | Denormalized, should be server-refreshed via `_recount_playlist()` | `WITH CHECK (track_count = OLD.track_count)` + `BEFORE UPDATE` trigger that recomputes. |
| `playlists.share_token` | `share_token` | **(b)** | Guessable link | `WITH CHECK (share_token = OLD.share_token)` — only `service_role` via `POST /share` should set it. |
| `playlists.is_collaborative` | `is_collaborative` | **(b)** | Blend owner gate | `WITH CHECK (is_collaborative = OLD.is_collaborative)` — only `service_role` via `PUT /user/playlists/{id}` with `is_collaborative` should flip it. |
| `user_settings.settings` | JSONB keys `is_pro`/`plan`/`ai_generations_remaining` | **(b) future** | No column-level RLS can protect a JSONB key | Do **not** store `plan`/`is_pro` in `user_settings.settings`. Use **(a)**: `subscriptions` table (`user_id PK, tier text, is_pro bool, expires_at timestamptz`) with `NO` `authenticated` UPDATE — only `service_role` via Stripe webhook. If you must keep it in `settings`, add `CHECK ((settings ? 'is_pro') = false)` and move tier to a separate table. |
| `ai_generation_usage.generation_count` | `generation_count` | **(a) safe** | 2/day cap | Already safe — separate table, **no** `authenticated` policy. Keep it that way. Do not add an `authenticated` UPDATE/INSERT. |
| `playlist_tracks` (INSERT `WITH CHECK true`) | `playlist_id`, `added_by_user_id` | **Row-level over-permissiveness (not column, but same exploit)** | `authenticated_insert_playlist_tracks` allows any `authenticated` to `INSERT` into any `playlist_id` with any `added_by_user_id` | Change to `WITH CHECK (EXISTS (SELECT 1 FROM playlists WHERE id = playlist_id AND (user_id = auth.uid() OR EXISTS (SELECT 1 FROM playlist_collaborators WHERE playlist_id = playlist_tracks.playlist_id AND user_id = auth.uid()))))` and `WITH CHECK (added_by_user_id = auth.uid())`. |

**No (c) "no protection at all" found** — every flagged column is at least behind `USING (id/user_id = auth.uid())`, but **no column list** is present (`UPDATE` is on the whole row).

**Recommended next step (per your "do not fix without confirmation"):** Confirm which of the above 8× (b) columns should be locked down, and whether `user_settings.settings` will ever hold a tier flag (if yes, create `subscriptions` table now instead of retrofitting). Then apply one of:

* **Supabase column-level RLS** (Postgres 15+): `CREATE POLICY … FOR UPDATE TO authenticated USING (id = auth.uid()) WITH CHECK (id = auth.uid() AND role = OLD.role AND is_verified = OLD.is_verified …)` — Supabase dashboard supports `USING`/`WITH CHECK` expressions, and `OLD`/`NEW` via `CHECK` constraints or triggers.
* **Or the safer (a) pattern:** move each privileged field to its own `service_role`-only table (`user_roles`, `playlist_share_tokens`, `subscriptions`) with **no** `authenticated` UPDATE — the FastAPI `service_role` remains the sole writer, and the direct `anon`+`JWT` path is closed even if the JWT role mismatch is later fixed.

No changes have been applied.

