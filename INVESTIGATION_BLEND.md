# Blend (Collaborative Playlists) — Investigation Report

**Date:** 2026-08-21
**Status:** Investigation complete, implementation not started

---

## Executive Summary

Blend is a playlist that multiple users can each add songs to, distinct from the existing single-owner sharing model. The database already has half the scaffolding (`playlist_collaborators` table, `playlists.is_collaborative` flag) but neither the backend nor the Android app reference them. The main work is wiring up permissions, building an invite flow (reusing the existing share_token mechanism), adding track attribution, and minimal UI changes.

---

## What Already Exists (Confirmed)

### Database (Supabase)

**`playlists` table — already has:**
- `is_collaborative` boolean, default `false` — never read or written by backend
- `share_token` text, nullable — used for read-only sharing today
- `user_id` uuid FK to users — the single owner
- `track_count` integer — denormalized count, updated after each add/remove

**`playlist_collaborators` table — exists, never used:**

| Column | Type | Constraints |
|---|---|---|
| `id` | uuid | PK, gen_random_uuid() |
| `playlist_id` | uuid | FK to playlists(id) |
| `user_id` | uuid | FK to users(id) |
| `added_at` | timestamptz | default now() |

- 0 rows
- RLS enabled: authenticated users can INSERT and SELECT; service_role can ALL
- No UPDATE or DELETE policy (no way to remove a collaborator via RLS)
- No unique constraint on (playlist_id, user_id) — duplicate memberships possible

**`playlist_tracks` table — no attribution:**

| Column | Type | Constraints |
|---|---|---|
| `id` | uuid | PK |
| `playlist_id` | uuid | FK to playlists(id) |
| `track_id` | text | FK to tracks(id) |
| `position` | integer | default 0 |
| `added_at` | timestamptz | |

- Missing `added_by_user_id` column — needed to show "who added this track"

**`notifications` table — exists, never written to:**
- Has columns: id, user_id, title, body, type, read, data, created_at
- 0 rows, no backend endpoints write to it
- No push notification (FCM) infrastructure exists

### Backend (backend-auth/routers/user.py)

**All playlist endpoints:**

| Method | Path | Function | Description |
|--------|------|----------|-------------|
| GET | `/user/playlists` | `get_playlists` | List all playlists for authenticated user (with tracks), ordered by updated_at desc |
| GET | `/user/playlists/{playlist_id}` | `get_playlist` | Get a single owned playlist with tracks |
| POST | `/user/playlists` | `create_playlist` | Create playlist (name, cover_url). Auto-generates share_token |
| PUT | `/user/playlists/{playlist_id}` | `update_playlist` | Update name/cover_url. Bumps updated_at |
| DELETE | `/user/playlists/{playlist_id}` | `delete_playlist` | Delete playlist and its tracks (cascade via FK) |
| POST | `/user/playlists/{playlist_id}/tracks` | `add_playlist_track` | Add a track at a given position (or append) |
| DELETE | `/user/playlists/{playlist_id}/tracks/{track_id}` | `remove_playlist_track` | Remove a track from a playlist |
| POST | `/user/playlists/{playlist_id}/share` | `share_playlist` | Get/create share token (idempotent) |
| DELETE | `/user/playlists/{playlist_id}/share` | `unshare_playlist` | Set share_token = None |
| GET | `/share/playlists/{token}` | `get_shared_playlist` | Public, no auth. Returns tracks + owner info |

**`_get_owned_playlist` — the permission wall:**

```python
def _get_owned_playlist(db, user_id: str, playlist_id: str) -> dict:
    playlist = (
        db.table("playlists")
        .select("*, playlist_tracks(track_id, position, added_at, tracks(*))")
        .eq("id", playlist_id)
        .execute()
    )
    if not playlist.data:
        raise HTTPException(status_code=404, detail="Playlist not found")
    playlist = playlist.data[0]
    if playlist["user_id"] != user_id:
        raise HTTPException(status_code=404, detail="Playlist not found")
    return playlist
```

- Two-layer check: must exist, then user_id must match token subject
- Returns 404 (not 403) in both cases to avoid leaking existence
- Used by: add_playlist_track, remove_playlist_track, update_playlist, delete_playlist, share/unshare

**Position / ordering:**
- `playlist_tracks.position` is an integer
- On add: if `body.position` is None, auto-appends as `max(existing positions) + 1` (or 0 if empty)
- If position is provided, used as-is — no gap maintenance
- On remove: track row deleted, remaining positions NOT shifted — gaps accumulate
- Display: sorts by `position ASC` before building response
- No dedicated reorder endpoint

**Sharing flow (share_token):**
1. On playlist creation: `secrets.token_urlsafe(16)` generates 22-char URL-safe token
2. `POST /share`: Idempotent — returns existing token or generates new one
3. `DELETE /share`: Sets share_token = NULL, all existing deep links immediately invalid
4. Public access: `GET /share/playlists/{token}` — no auth required, returns tracks + owner {username, avatar_url}
5. Web preview: `routers/web.py` serves HTML/OG-meta page for the same token

**Transaction handling / locking:**
- None. All operations are individual Supabase PostgREST calls
- No explicit transactions or row-level locks
- `_get_owned_playlist` + mutation: each endpoint verifies ownership then mutates in separate call — no locking between check and write
- `track_count` denormalized column updated after each add/remove via separate count + update — could drift on error

**Request/response schemas:**

```python
class PlaylistCreateRequest(BaseModel):
    name: str = Field(min_length=1, max_length=256)
    cover_url: Optional[str] = Field(default=None, max_length=2048)

class PlaylistUpdateRequest(BaseModel):
    name: Optional[str] = Field(default=None, min_length=1, max_length=256)
    cover_url: Optional[str] = Field(default=None, max_length=2048)

class AddPlaylistTrackRequest(BaseModel):
    track: TrackPayload
    position: Optional[int] = None  # None = append at end

class TrackPayload(BaseModel):
    id: str
    title: str
    artist: str
    album: Optional[str]
    duration: int = 0
    artwork_url: Optional[str]
    source: str = "youtube"
    genre: Optional[str]
    year: Optional[int]
```

**Create response (201):**
```json
{
  "id": "...", "user_id": "...", "name": "...", "cover_url": null,
  "share_token": "...", "created_at": "...", "updated_at": "...",
  "track_count": 0, "tracks": []
}
```

**Get/list response:**
```json
{
  "id": "...", "name": "...", "cover_url": "...", "track_count": 3,
  "tracks": [
    { "position": 0, "added_at": "...", "track": { "id": "...", "title": "...", "..." : "..." } }
  ]
}
```

**Public share response:**
```json
{
  "id": "...", "name": "...", "cover_url": "...", "share_token": "...",
  "track_count": 3, "tracks": [],
  "owner": { "username": "...", "avatar_url": "..." }
}
```

### Android App

**PlaylistEntity:**
```kotlin
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String = generatePlaylistId(),  // "LP" + 8 random chars
    val name: String,
    val browseId: String? = null,               // YouTube playlist ID (null = local-only)
    val createdAt: LocalDateTime? = LocalDateTime.now(),
    val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,    // non-null = liked
    val remoteSongCount: Int? = null,
    val thumbnailUrl: String? = null,
    val isLocal: Boolean = false,               // true = never synced to YT
    val isAutoSync: Boolean = false
)
```
- No `ownerId`, `collaborators`, or `permissions` fields
- No `isCollaborative` field

**How tracks are added locally:**
- `DatabaseDao.addSongsToPlaylist(playlist, songs, prepend)`:
  - prepend=true (default): shifts all existing positions by songsToInsert.size, inserts at positions 0..N-1
  - prepend=false: appends after current songCount
- Each `PlaylistSongMap` gets: playlistId, songId, position, optional setVideoId
- If playlist has browseId, also pushes to YT Music
- For non-YT playlists, `SyncUtils.addToPlaylist()` pushes to Soundsphere backend

**How sharing works in UI:**
1. Generating share link (`PlaylistMenu.kt`): User taps Share -> `syncRepository.getPlaylistShareToken(playlistId)` -> `POST /user/playlists/{serverId}/share` -> returns token -> share URL `https://api.soundsphere.name.ng/share/playlists/{token}`
2. Receiving shared link (`SharedPlaylistScreen.kt`, `SharedPlaylistViewModel.kt`): Deep link `soundsphere://p/{token}` routes to `shared_playlist/{token}` -> `SyncService.getSharedPlaylist(token)` -> no auth required -> returns SharedPlaylist with name, cover, owner username, track list
3. Recipient can play tracks but **cannot edit** — strictly read-only

**Collaborative concepts:**
- None exist in the app. Zero references to `playlist_collaborators`, `is_collaborative`, or any multi-user concept.
- The only "collaborators" reference in Kotlin is in `AboutScreen.kt` (credits/contributors section) — unrelated.

---

## Schema Changes Needed

### Migration (1 migration)

```sql
-- 007_blend_collaborative_playlists.sql

-- Add track attribution
ALTER TABLE playlist_tracks
  ADD COLUMN IF NOT EXISTS added_by_user_id uuid REFERENCES users(id);

-- Prevent duplicate memberships
ALTER TABLE playlist_collaborators
  ADD CONSTRAINT unique_playlist_user UNIQUE (playlist_id, user_id);
```

**No new tables needed.** `playlist_collaborators` and `playlists.is_collaborative` already exist.

---

## Permission Changes

### New function: `_get_accessible_playlist`

Drop-in replacement for `_get_owned_playlist` on endpoints that should open to collaborators:

```python
def _get_accessible_playlist(db, user_id: str, playlist_id: str) -> dict:
    """Allow access if user owns the playlist OR is a collaborator."""
    playlist = (
        db.table("playlists")
        .select("*, playlist_tracks(track_id, position, added_at, tracks(*))")
        .eq("id", playlist_id)
        .execute()
    )
    if not playlist.data:
        raise HTTPException(status_code=404, detail="Playlist not found")
    playlist = playlist.data[0]

    # Owner always has access
    if playlist["user_id"] == user_id:
        return playlist

    # Check collaborator membership
    collab = (
        db.table("playlist_collaborators")
        .select("id")
        .eq("playlist_id", playlist_id)
        .eq("user_id", user_id)
        .limit(1)
        .execute()
    )
    if collab.data:
        return playlist

    raise HTTPException(status_code=404, detail="Playlist not found")
```

### Permission split per endpoint

| Endpoint | Owner | Collaborator | Implementation |
|---|---|---|---|
| `GET /user/playlists` (list) | own playlists | collaborated playlists | Modify query to UNION owned + collaborated |
| `GET /user/playlists/{id}` (detail) | yes | yes | Use `_get_accessible_playlist` |
| `POST /user/playlists/{id}/tracks` (add) | yes | yes | Use `_get_accessible_playlist`, pass `added_by_user_id` |
| `DELETE /user/playlists/{id}/tracks/{track_id}` (remove) | yes | yes | Use `_get_accessible_playlist` |
| `PUT /user/playlists/{id}` (rename/cover) | yes | no | Keep `_get_owned_playlist` |
| `DELETE /user/playlists/{id}` (delete) | yes | no | Keep `_get_owned_playlist` |
| `POST /user/playlists/{id}/share` (toggle share) | yes | no | Keep `_get_owned_playlist` |
| `DELETE /user/playlists/{id}/share` (revoke) | yes | no | Keep `_get_owned_playlist` |
| `POST /share/playlists/{token}/join` (NEW) | N/A | N/A | Auth required, checks is_collaborative, inserts member |

### `GET /user/playlists` query change

Currently: `WHERE user_id = token.subject`

For Blend: also return playlists where user is a collaborator:

```sql
SELECT DISTINCT p.*
FROM playlists p
LEFT JOIN playlist_collaborators pc ON pc.playlist_id = p.id
WHERE p.user_id = $1 OR pc.user_id = $1
ORDER BY p.updated_at DESC
```

---

## Conflict Handling

### The race condition

Two members add a track simultaneously. Both compute `max(position) + 1`, get the same value, both insert at the same position. Result: two tracks at the same position.

### How bad is it in practice?

For a casual music playlist, not very:
- Worst case: two tracks at the same position
- Display sorts by `position ASC, added_at ASC` — they appear adjacent and in chronological order
- No data loss, no crash — just potentially unexpected ordering
- The position collision window is sub-second

### Options

1. **Do nothing (recommended for v1):** Accept the rare collision. Self-resolves visually (adjacent tracks). Document as known limitation.
2. **Transaction with row lock:** Wrap read-max + insert in `BEGIN; SELECT ... FOR UPDATE; INSERT; COMMIT`. Supabase PostgREST doesn't expose raw SQL transactions — requires a PostgreSQL function (`CREATE FUNCTION add_playlist_track(...)`) called via RPC. Adds complexity for marginal benefit.
3. **Fractional ordering:** Replace integer positions with floats (insert between 0.0 and 1.0). Eliminates read-before-write entirely. Requires schema migration, rework of all position logic, eventual compaction. Overkill for v1.

### Recommendation

Option 1 for v1. The position collision window is sub-second and the consequence is cosmetic. If it becomes a real issue, upgrade to a PostgreSQL function later.

### Real-time visibility

Two members adding tracks simultaneously won't see each other's additions until they refresh. This is acceptable for v1. Real-time would require websockets or polling infrastructure (already scoped in Batch 10 of `soundsphere_logged_decided.md`) — a separate, larger project.

Refresh-based is the right first version: members see new tracks the next time they open the playlist.

---

## Notifications

### Current state

- `notifications` table exists in Supabase with RLS — 0 rows, no backend endpoints write to it
- No push notification (FCM) infrastructure exists
- No notification screen or badge in the app

### Can Blend ship without push?

Yes. Members see new tracks the next time they open the playlist. This is how Spotify Blend works for most users — it's not real-time chat, it's a shared playlist you check periodically.

### Lightweight option (v1.1)

When a member adds a track, insert a row into `notifications` for each other member:

```sql
INSERT INTO notifications (user_id, title, body, type, data)
VALUES (
  $member_id,
  'New track added',
  $username || ' added "' || $track_title || '"',
  'info',
  jsonb_build_object('playlist_id', $playlist_id)
);
```

The app can poll this table on launch or when the playlist screen opens. No FCM needed. This gives a "you have new activity" signal without the complexity of push.

### Recommendation

Ship without notifications in v1. Add the `notifications` row writes in v1.1. Push notifications are a separate project.

---

## App-Side Scope

### Minimum viable changes

| Change | Scope | Files |
|---|---|---|
| "Invite to Blend" on playlist menu | Small | `PlaylistMenu.kt` — add menu item, call join endpoint |
| Blend indicator in Library | Trivial | `LibraryPlaylistsScreen.kt` — icon/badge on collaborative playlists |
| Member list in playlist detail | Medium | New composable in `LocalPlaylistScreen.kt` or `PlaylistDetailScreen.kt` |
| "Who added this" on tracks | Medium | `PlaylistSongMap` needs `addedByUserId` column, UI shows avatar/name per track |
| Accept blend invite via deep link | Small | `SharedPlaylistScreen.kt` — if `is_collaborative`, show "Join" button instead of read-only |
| Sync collaborative playlists | Medium | `SyncRepository.kt` — `GET /user/playlists` must return both owned + collaborated playlists |

### Visual distinction

A small icon (people/group icon) on the playlist thumbnail in the Library screen is sufficient. No major redesign needed.

### Database entity changes

`PlaylistSongMap` (Room entity) needs new column:
```kotlin
@Entity(...)
data class PlaylistSongMap(
    val playlistId: String,
    val songId: String,
    val position: Int,
    val setVideoId: String? = null,
    val addedByUserId: String? = null  // NEW
)
```

---

## Implementation Plan

### Phase 1: Backend (estimated 2-3 days)

1. **Migration 007** — Add `added_by_user_id` to `playlist_tracks`, add unique constraint to `playlist_collaborators`
2. **`_get_accessible_playlist`** — New function, used by list/detail/add/remove endpoints
3. **`GET /user/playlists`** — Update query to return owned + collaborated playlists
4. **`POST /share/playlists/{token}/join`** — New endpoint: auth required, check `is_collaborative`, insert into `playlist_collaborators`, set `is_collaborative = true` on first join, return full playlist
5. **`POST /user/playlists/{id}/tracks`** — Pass `added_by_user_id` from token subject
6. **`POST /user/playlists/{id}/collaborators`** — New endpoint: owner adds a user by user_id (for future in-app invite)
7. **`GET /user/playlists/{id}/collaborators`** — New endpoint: list members
8. **`DELETE /user/playlists/{id}/collaborators/{user_id}`** — New endpoint: owner removes a member

### Phase 2: Android (estimated 3-4 days)

1. **Database entity** — Add `addedByUserId` to `PlaylistSongMap`, Room migration
2. **SyncRepository** — Handle collaborative playlists in list/detail, new `joinPlaylist(token)` call
3. **SyncService** — New API calls: `joinPlaylist`, `getCollaborators`, `addCollaborator`, `removeCollaborator`
4. **PlaylistMenu.kt** — "Invite to Blend" action (generates share link for collaborative playlist)
5. **SharedPlaylistScreen.kt** — "Join" button when `is_collaborative = true`
6. **LibraryPlaylistsScreen.kt** — People icon badge on collaborative playlists
7. **LocalPlaylistScreen.kt** — Member list at top, "added by" avatar/name per track
8. **Deep link handling** — Route blend invites through the join flow

### Phase 3: Testing (estimated 1 day)

1. Two users joining the same playlist via share link
2. Both adding tracks simultaneously (position collision test)
3. Owner removing a collaborator
4. Collaborator trying owner-only actions (should fail)
5. Deep link invite flow end-to-end
6. Edge cases: joining a playlist you're already a member of, owner leaving their own playlist

### Total estimated effort: ~1 week

---

## Open Questions

1. **Should the owner be able to make a playlist collaborative retroactively?** (i.e., convert an existing single-owner playlist into a Blend) — Probably yes, via `PUT /playlists/{id}` setting `is_collaborative = true`.
2. **Should collaborators be able to remove tracks they didn't add?** — Recommendation: yes, for v1. Any member can add or remove any track. This keeps the permission model simple.
3. **Should there be a max member count?** — Recommendation: yes, cap at 8-10 members for v1 to prevent abuse and keep the UI clean.
4. **What happens when the owner deletes a collaborative playlist?** — Cascade delete handles this (playlist_tracks and playlist_collaborators both FK to playlists with cascade). All members lose access immediately.
5. **Should the share link still work for read-only after a playlist becomes collaborative?** — Recommendation: no. Once collaborative, the share link should either add you as a member (if logged in) or prompt you to log in. Read-only access via share link should only apply to non-collaborative playlists.

---

## Second Backend Service — How It Would Connect

### Part 1: Existing Backend Auth System

**Login flow (backend-auth/routers/auth.py):**

1. User sends `POST /auth/login` with `{"email": "...", "password": "..."}`
2. Backend looks up user in Supabase `users` table by email
3. Verifies password against `password_hash` using `verify_password()`
4. Checks `is_verified` flag
5. Calls `create_token(user["id"], role=user.get("role", "user"))` to generate JWT
6. Returns `TokenResponse(token=jwt_string, user=UserResponse(...))`

**JWT creation (auth/jwt.py:12-21):**

```python
def create_token(user_id: str, role: str = "user") -> str:
    secret = os.getenv("JWT_SECRET")
    expire_minutes = int(os.getenv("JWT_EXPIRE_MINUTES", "10080"))
    payload = {
        "sub": user_id,          # user's UUID
        "role": role,            # "user" or "admin"
        "exp": datetime.now(timezone.utc) + timedelta(minutes=expire_minutes),
    }
    return jwt.encode(payload, secret, algorithm=ALGORITHM)
```

- **Algorithm:** HS256 (HMAC-SHA256)
- **Claims:** `sub` (user UUID), `role` ("user"/"admin"), `exp` (7 days default)
- **No** `iat`, `iss`, `aud`, or `jti` claims
- **Expiration:** 10080 minutes = 7 days, configurable via `JWT_EXPIRE_MINUTES`

**JWT signing secret:**

- **Variable name:** `JWT_SECRET`
- **Type:** Simple string, loaded from environment via `os.getenv("JWT_SECRET")`
- **No default** — will be None if unset (would fail at encode/decode time)
- **Configuration:** Easy to复制 — just set the identical `JWT_SECRET` env var on the second service
- **No derivation, no key rotation, no complexity** — it's a plain shared secret

**Token verification (auth/jwt.py:24-31):**

```python
def decode_token(token: str) -> dict:
    secret = os.getenv("JWT_SECRET")
    try:
        payload = jwt.decode(token, secret, algorithms=[ALGORITHM])
        return payload
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
```

**Verification is PURELY CRYPTOGRAPHIC — zero database lookup.**

`decode_token` calls `jose.jwt.decode()` with the secret and algorithm. It validates the signature and expiration, then returns the payload. No DB interaction whatsoever.

All four dependency functions (`get_current_user`, `get_current_user_info`, `get_optional_user`, `admin_required`) call `decode_token` — purely cryptographic. The `_track_user` side-effect does hit the DB for activity logging, but that's not part of auth verification.

**This is the critical finding: a second backend can verify the same JWT by just setting the identical `JWT_SECRET` environment variable. No database connection, no API calls to the first backend, no token exchange — just copy the secret.**

### Part 2: How the Android App Talks to the Backend

**Backend URL configuration (app/build.gradle.kts):**

```kotlin
// Default (release)
buildConfigField("String", "API_BASE_URL", "\"https://api.soundsphere.name.ng\"")
buildConfigField("String", "API_FALLBACK_BASE_URL", "\"https://soundsphere-auth.onrender.com\"")

// Debug override
val authDevUrl = project.findProperty("AUTH_DEV_BASE_URL") as? String
if (authDevUrl != null) {
    buildConfigField("String", "API_BASE_URL", "\"$authDevUrl\"")
}
```

- Primary URL: `https://api.soundsphere.name.ng`
- Fallback URL: `https://soundsphere-auth.onrender.com`
- Debug builds can override primary via `AUTH_DEV_BASE_URL` in `local.properties`
- URLs are `BuildConfig` fields — compile-time constants, not runtime-configurable

**Failover mechanism (BackendEndpoint.kt):**

```kotlin
object BackendEndpoint {
    @Volatile var current: String = BuildConfig.API_BASE_URL
    private val primary = BuildConfig.API_BASE_URL
    private val fallback = BuildConfig.API_FALLBACK_BASE_URL

    fun markFailure() { current = fallback }   // sticky switch
    fun markSuccess() { current = primary }     // switch back
}
```

- Two-state machine: primary or fallback
- Sticky failover — switches to fallback on first IOException, stays there until success
- No multi-backend routing, no endpoint list, no round-robin

**Token storage (AuthRepository.kt):**

- Uses **EncryptedSharedPreferences** backed by Android Keystore (`MasterKey` with `AES256_GCM`)
- Preferences file: `"soundsphere_auth"`, key: `"auth_token"`
- Falls back to plain `SharedPreferences` (with `_fallback` suffix) if Keystore init fails on broken OEM devices
- `@Singleton`, injected via Hilt
- `saveToken(token)` / `getToken()` / `clearToken()` — simple key-value operations

**Token attachment to requests (SyncService.kt):**

- **No OkHttp interceptor** — token is attached manually per-request
- Every public method takes a `token: String` parameter
- The caller (`SyncRepository`) fetches it from `authRepository.getToken()` before each call

```kotlin
Request.Builder().url("$base$path").header("Authorization", "Bearer $token")
```

**SyncService structure (SyncService.kt):**

- Kotlin `object` (singleton), not Hilt-managed
- Private `OkHttpClient` with 30s timeouts, no interceptors
- `execute()` — calls `attempt()` against `BackendEndpoint.current()`, catches IOException, marks failure, retries once against fallback
- `authRequest()` — builds Request with Authorization header
- Data classes: `SyncTrack`, `SyncPlaylist`, `SyncPlaylistTrack`, `SyncLikedEntry`, `SyncHistoryEntry`, `SyncArtist`, `SharedPlaylist`
- Endpoint groups: Liked tracks, Playlists, History, Followed artists, Settings, AI playlist, Shared playlists
- JSON parsing is manual via `org.json` (no Moshi/Gson)

**SyncRepository structure (SyncRepository.kt):**

- `@Singleton`, Hilt-injected
- Pull (server->local): `pullAll()` runs behind a Mutex, calls `pullLikes`, `pullPlaylists`, `pullHistory`, etc. — each isolated via `runPullStage()` so one failure doesn't abort others
- Push (local->server): fire-and-forget coroutines
- Retry logic: `retryNetwork()` retries up to 3 times with linear backoff
- 401 handling: `handleFailure()` calls `authRepository.clearToken()` on UnauthorizedException
- Local-first semantics: pulls only ADD remote data (union merge), never deletes local rows
- Playlist ID mapping: persisted to DataStore, maps local `LP...` IDs to server UUIDs

**Single-backend assumptions:**

The code has a **two-backend** design (primary + fallback), not single-backend:
- `BackendEndpoint` holds exactly one primary URL and one fallback URL — no dynamic/multi-backend routing
- Both URLs hardcoded in `build.gradle.kts` as `BuildConfig` fields
- The failover is sticky (two-state machine)
- Deep-link check in `MainActivity.kt` only checks for `soundsphere` scheme or `soundsphere.name.ng` host — no multi-domain awareness

### Part 3: Deployment Configuration

**Hosting:** Render (no Infrastructure as Code — dashboard-configured)

**Start command:** `uvicorn main:app --host 0.0.0.0 --port $PORT`

**Root dir:** `backend-auth/`

**Production URL:** `https://soundsphere-auth.onrender.com`
**Custom domain:** `api.soundsphere.name.ng`

**Framework:** FastAPI 0.111.0 + uvicorn 0.29.0
**Python:** 3.13.5 (per `.python-version`)

**Key dependencies:**
- supabase 2.4.6 (Python client)
- python-jose (JWT)
- slowapi (rate limiting)
- pydantic 2.11.1
- httpx 0.27.2

**Replicability for a second service:** Straightforward. Same hosting platform, same general setup, just pointed at a different database and running different code.

### Part 4: Database Connection

**Connection mechanism:** Supabase Python client library — no direct Postgres connection.

```python
# db/supabase.py
_supabase_client: Client | None = None

def get_supabase() -> Client:
    global _supabase_client
    if _supabase_client is None:
        url = os.getenv("SUPABASE_URL")
        key = os.getenv("SUPABASE_SERVICE_KEY")
        _supabase_client = create_client(url, key)
    return _supabase_client
```

**Environment variables:**

| Variable | Purpose | Required |
|---|---|---|
| `SUPABASE_URL` | Supabase project URL (e.g. `https://<ref>.supabase.co`) | Yes |
| `SUPABASE_SERVICE_KEY` | Service role key (bypasses ALL RLS) | Yes |
| `JWT_SECRET` | HS256 signing secret for app JWTs | Yes |
| `GMAIL_USER` | SMTP sender for OTP emails | Yes |
| `GMAIL_APP_PASSWORD` | SMTP password | Yes |
| `JWT_EXPIRE_MINUTES` | Default 10080 (7 days) | No |
| `ALLOWED_HOSTS` | Comma-separated trusted hosts | No |
| `GROQ_API_KEY` | AI playlist generation | No |

**Critical detail:** The service role key bypasses **all** Row Level Security. The backend is the sole data access layer — the Android app never talks directly to Supabase.

---

## How a Second Backend Would Work — Concrete Plan

### What the second backend needs

1. **Same `JWT_SECRET`** — copy the exact same value from the existing backend. This lets it verify tokens from the same login, with zero database lookups.
2. **Its own `SUPABASE_URL` + `SUPABASE_SERVICE_KEY`** — pointing to a different Supabase project (the new feature's separate database).
3. **Same deployment pattern** — Render free tier, uvicorn start command, Python 3.13.

### What changes on the Android app

The app currently talks to one backend at a time (primary or fallback). To talk to two independent backends:

1. **New `BlendService.kt`** — a second `object` (like `SyncService`) but pointed at the blend backend URL. Same pattern: `execute()`, `authRequest()`, manual JSON parsing. Copy-paste of SyncService structure, different base URL, different endpoints.

2. **New `BlendRepository.kt`** — a second `@Singleton` Hilt class (like `SyncRepository`). Handles pull/push for collaborative playlists only. Same retry logic, same `authRepository.getToken()` call for the token.

3. **Backend URL for blend** — add a third `BuildConfig` field:

```kotlin
buildConfigField("String", "BLEND_BASE_URL", "\"https://soundsphere-blend.onrender.com\"")
```

Or reuse `BackendEndpoint` pattern with a second endpoint object:

```kotlin
object BlendEndpoint {
    @Volatile var current: String = BuildConfig.BLEND_BASE_URL
}
```

4. **No token changes needed** — the same JWT from the existing login works on both backends. `authRepository.getToken()` returns the same token, `BlendService` attaches it the same way.

5. **No login changes needed** — user logs in through existing backend, gets JWT, uses it on both backends.

### What the second backend does NOT need

- No user table — it reads `user_id` from the JWT `sub` claim
- No login/signup endpoints — authentication is handled by the first backend
- No password storage — same JWT secret means it trusts the first backend's tokens
- No Supabase Auth integration — it doesn't create users, it just reads the JWT

### Database isolation

The two databases are completely independent:
- Existing backend -> existing Supabase project (`ysfktparruosuegzdnwt`)
- Second backend -> new Supabase project (separate ref)
- No cross-database queries, no shared tables, no conflicts
- Each has its own service role key, its own RLS policies, its own schema

### Security considerations

1. **JWT_SECRET must be identical** — if they differ, tokens from one backend won't verify on the other
2. **Service role keys are separate** — each backend only has write access to its own database
3. **The second backend could be granted read-only access** to specific tables if needed (via a separate Supabase API key or direct Postgres connection with restricted permissions)
4. **Token expiration is shared** — a token valid for 7 days on the first backend is also valid for 7 days on the second. There's no way to independently control expiration per backend with the same JWT_SECRET.
5. **Role claims are shared** — if the first backend sets `role: "admin"`, the second backend sees it too. The second backend should check its own authorization logic if needed.

---

## "Recently Played" on Home Screen — Investigation

### Part 1: What Already Exists for History

**Backend `history` table (Supabase):**

| Column | Type | Constraints |
|---|---|---|
| `id` | uuid | PK, gen_random_uuid() |
| `user_id` | uuid | FK to users(id) ON DELETE CASCADE |
| `track_id` | text | FK to tracks(id) ON DELETE CASCADE |
| `played_at` | timestamptz | default now() |

- Indexed on `(user_id, played_at DESC)` — main query path
- Indexed on `(track_id)` — lookup by track
- **No unique constraint** — duplicate plays of the same song are separate rows
- **Per-song only** — no playlist ID, no queue ID, no source context

**Backend endpoints:**

| Method | Path | Description |
|---|---|---|
| GET | `/user/history` | Returns up to 500 most recent entries, newest first |
| POST | `/user/history` | Adds one entry (upserts track metadata first) |
| DELETE | `/user/history` | Clears ALL history for the user |

GET endpoint code:
```python
@router.get("/history")
async def get_history(request, user_id):
    rows = db.table("history")
        .select("played_at, tracks(*)")
        .eq("user_id", user_id)
        .order("played_at", desc=True)
        .limit(500)
        .execute()
    return {"history": [{"played_at": ..., "track": {...}}]}
```

POST endpoint code:
```python
@router.post("/history")
async def add_history(body: HistoryAddRequest, request, user_id):
    _upsert_track(db, body.track)
    db.table("history").insert({
        "user_id": user_id,
        "track_id": body.track.id,
        "played_at": body.played_at or datetime.now(timezone.utc).isoformat(),
    }).execute()
```

Request schema:
```python
class HistoryAddRequest(BaseModel):
    track: TrackPayload     # id, title, artist, album, duration, artwork_url, source, genre, year
    played_at: Optional[str] = None
```

**Android Room `event` table:**

| Column | Type | Notes |
|---|---|---|
| `id` | Long | PK, auto-generate |
| `songId` | String | FK to song table, indexed |
| `timestamp` | LocalDateTime | when played |
| `playTime` | Long | actual playback duration in ms |

- Related: `EventWithSong` wraps `Event` + joined `Song` data
- Additional local-only tables: `playCount` (monthly per-song counts), `song.totalPlayTime` (cumulative)

**How history is recorded (MusicService.kt:3530-3577):**

```kotlin
override fun onPlaybackStatsReady(eventTime, playbackStats) {
    val historyDurationMs = dataStore[HistoryDuration]?.times(1000f) ?: 30000f  // default 30s
    if (playbackStats.totalPlayTimeMs >= historyDurationMs
        && !dataStore.get(PauseListenHistoryKey, false)
    ) {
        database.query {
            incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
            insert(Event(
                songId = mediaItem.mediaId,
                timestamp = LocalDateTime.now(),
                playTime = playbackStats.totalPlayTimeMs,
            ))
            syncRepository.historyAdded(mediaItem.mediaId)
        }
    }
}
```

- Threshold: 30 seconds of actual play time (configurable)
- Can be paused via `PauseListenHistoryKey` preference
- Trigger: `onPlaybackStatsReady` callback (track finishes or is skipped after threshold)

**How history is synced:**

Push (local -> server):
```kotlin
private suspend fun pushHistory(songId: String) {
    val token = authRepository.getToken() ?: return
    val song = database.songEntity(songId) ?: return
    SyncService.addHistory(token, song.toSyncTrack(), LocalDateTime.now().toString())
}
```
- Fire-and-forget, triggered immediately after local insert
- Retried up to 3 times with 1s backoff

Pull (server -> local):
```kotlin
private suspend fun pullHistory(token: String) {
    val existingSongIds = database.events().first().map { it.event.songId }.toSet()
    for (entry in SyncService.getHistory(token).getOrThrow()) {
        val remote = entry.track
        if (remote.id in existingSongIds) continue   // skip duplicates
        if (database.songEntity(remote.id) == null) {
            database.insertSongWithArtists(...)
        }
        database.insert(Event(
            songId = remote.id,
            timestamp = parseTimestamp(entry.playedAt) ?: LocalDateTime.now(),
            playTime = 0L,   // server doesn't store playTime
        ))
    }
}
```

**Key sync behavior:**
- **Add-only union merge** — never deletes local rows
- Dedup by song ID (not by timestamp) — same song played twice from different times is deduplicated
- `playTime` on pull is always `0L` (server doesn't store it)
- No cap on entries inserted — grows unboundedly
- Server returns max 500 entries (read cap, not storage cap)

**Playlist-level listening tracking: DOES NOT EXIST.**

There is no separate tracking of "the user was playing playlist X." The `event` table only stores `songId`, `timestamp`, and `playTime` — no `playlistId`, `queueId`, or source context. When a song plays from a playlist, album, search, or radio, it creates the same generic `Event` row.

---

### Part 2: What "Recently Played" Needs for Playlists

**Current gap:** No way to distinguish "played from playlist X" vs "played from search" vs "played from album."

**What would be needed:**

Option A: Add `source_type` and `source_id` columns to the existing `event` table:
```sql
ALTER TABLE event ADD COLUMN source_type TEXT;  -- 'playlist', 'album', 'artist', 'search', 'radio', null
ALTER TABLE event ADD COLUMN source_id TEXT;     -- playlist ID, album browseId, etc.
```
- Pros: No new table, single query for "recently played songs" and "recently played playlists"
- Cons: Schema change, migration, every Event insert needs updating

Option B: New `recently_played` table:
```sql
CREATE TABLE recently_played (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    source_type TEXT NOT NULL,      -- 'song', 'playlist', 'album', 'artist'
    source_id TEXT NOT NULL,        -- song ID, playlist ID, etc.
    source_name TEXT,               -- display name (denormalized)
    source_thumbnail TEXT,          -- thumbnail URL (denormalized)
    played_at TIMESTAMPTZ DEFAULT now()
);
```
- Pros: Clean separation, easy to query "recently played playlists" separately from songs
- Cons: New table, new sync logic, data duplication with existing history

**Recommendation:** Option B. A dedicated `recently_played` table is cleaner because:
1. It's inherently capped (last N entries per source type)
2. It needs different sync behavior (pruning, not just adding)
3. It tracks different data (source_type, source_id, display name) vs raw history (track_id, play_time)
4. It avoids polluting the existing history table with NULLs for non-playlist plays

---

### Part 3: Home Screen Structure

**File:** `app/src/main/kotlin/com/soundsphere/music/ui/screens/HomeScreen.kt` (2624 lines)

**ViewModel:** `app/src/main/kotlin/com/soundsphere/music/viewmodels/HomeViewModel.kt` (830 lines)

**Current sections (default order):**

| # | Section | Data Source |
|---|---------|-------------|
| 1 | Wrapped Card | DataStore prefs + WrappedManager |
| 2 | Speed Dial | Room DB (pinned items) + keepListening + quickPicks |
| 3 | Quick Picks | Room DB (related songs from 5 most recent events + top 10 by play time) |
| 4 | From The Community | YouTube Innertube (seeded by most played artists/songs) |
| 5 | Daily Discover | YouTube Innertube (seeded by 5 random liked songs) |
| 6 | Keep Listening | Room DB (most played songs/albums/artists, last 2 weeks, with offset) |
| 7 | Account Playlists | YouTube Innertube (liked playlists) |
| 8 | Forgotten Favorites | Room DB (high old playtime, low recent playtime) |
| 9 | Similar Recommendations | YouTube Innertube (seeded by most played) |
| 10 | YouTube Home Page | YouTube Innertube (generic home feed) |
| 11 | Mood & Genres | YouTube Innertube (explore page) |

**"Keep Listening" is the closest analogue** but it's based on frequency (most played), not recency (last played). It queries `mostPlayedSongs` with offset 5 to skip the top 5, over the last 2 weeks.

**No "Recently Played" or "Jump Back In" section exists.**

**Where it would fit:** After Speed Dial (weight ~85-95), before or after Quick Picks. "Recently played" is typically one of the first things users want to see.

**Data source needed:** `database.events()` (already available, ordered by `rowId DESC`) or a new DAO query returning recent distinct songs. For playlists, would need the new `recently_played` table from Part 2.

---

### Part 4: Sync Implications

**Current merge model: add-only union, never deletes.**

This is explicitly documented in SyncRepository.kt:
> "pulls only ADD remote data locally (likes/playlists/history), they never delete local rows, so a device that is offline never loses state"

**Does "Recently Played" fit this model?** No, for three reasons:

1. **Recency requires pruning.** Old entries should fall away. If a user listened to a song last year, it shouldn't show in "Recently Played" today. The add-only model would keep it forever.

2. **Dedup by song ID is wrong for recency.** If a user played Song A on Monday, then Song B on Tuesday, then Song A again on Wednesday, the current dedup (skip if song ID exists) would only record Monday's play. But for "Recently Played," Wednesday's play is the relevant one.

3. **Capping is needed.** "Recently Played" should show last 20-50 entries. The current model has no cap — it accumulates forever.

**What new sync behavior is needed:**

- **Server-side:** A new endpoint or modified query that returns only the last N entries per user, with dedup by source_id keeping only the most recent play. Old entries should be pruned server-side (DELETE WHERE played_at < threshold).

- **Client-side:** On pull, replace (not merge) the local `recently_played` entries. This is a fundamentally different sync operation — it's a "replace with latest" model, not "add to existing."

- **Conflict handling:** If two devices play songs simultaneously, the server should keep both (they have different timestamps), and the next pull on either device gets the merged set. This naturally works because the server stores all entries and the client pulls the last N.

**This is a new sync pattern not currently used anywhere in the app.** The existing patterns are:
- Add-only union (history, likes, playlists)
- Overwrite (settings — one value wins)
- "Recently Played" needs: **replace-with-latest** (prune old, keep newest N)

---

### Concrete Recommendation

**"Recently Played" needs new infrastructure, not just a new Home screen section.**

**What exists and can be reused:**
- The `event` table and existing history sync (for raw song history)
- The `SyncRepository` retry/push/pull framework (pattern, not exact code)
- The `HomeSection` sealed class and `HomeViewModel` data loading pattern

**What needs to be built new:**

| Component | What | Why |
|---|---|---|
| New table | `recently_played` (user_id, source_type, source_id, source_name, source_thumbnail, played_at) | Capped, prunable, tracks source context |
| New backend endpoints | `GET /user/recently-played` (last 50), `POST /user/recently-played` (add entry), `DELETE /user/recently-played` (clear) | Dedicated query for recency |
| New Room entity | `RecentlyPlayedEntity` with DAO | Local storage for offline access |
| New sync logic | "Replace-with-latest" pull pattern | Different from add-only union |
| New Home section | `HomeSection.RecentlyPlayed` in HomeScreen.kt | UI |
| Playback hook | Record source context when playback starts | Know which playlist/album the song came from |

**Estimated scope:**
- Backend: ~1 day (new table, 3 endpoints)
- Android: ~2-3 days (entity, DAO, sync, Home section, playback hook)
- Total: ~3 days for a solid v1

**Alternative (smaller scope, no playlist tracking):**
If playlist-level tracking is deferred, "Recently Played" could use the existing `event` table directly with a new DAO query (`SELECT DISTINCT songId FROM event ORDER BY rowId DESC LIMIT 30`). This requires only:
- New Home section (~0.5 day)
- No backend changes
- No new sync logic
- But: no playlist tracking, no cross-device recency guarantee (local events only), no source context
