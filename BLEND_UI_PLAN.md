# Blend — UI/UX Design Plan (derived from INVESTIGATION_BLEND.md)

> Companion to `INVESTIGATION_BLEND.md` (702 lines). This file is the **build-ready UI spec** — icon, naming, screens, and track attribution. No code was changed for this plan.

---

## 1. Naming

**Primary name: `Blend`**

* Why: Short, verb-able ("Blend this playlist"), matches the mental model users already have from Spotify Blend. The DB already uses `is_collaborative` internally, but user-facing copy should not say "collaborative" — too long and formal.
* Alternatives considered:
  * `Collaborative playlist` — accurate but 22 chars, truncates in Library grid.
  * `Shared Mix` — implies algorithm, not user curation.
  * `Group Playlist` — generic.
* **Recommendation:** Use **`Blend`** as the product name, with a one-line subtitle wherever space allows: `Blend • Collaborative playlist`. The Library badge and playlist detail header both show "Blend" as a label, not as a replacement for the playlist's own name. Example: playlist name stays "Road Trip 2026", header shows `Road Trip 2026` + `Blend • 3 members` underneath.

**Strings to add** (all in `app/src/main/res/values/soundsphere_strings.xml` — the only string file per `AGENTS.md`):

* `blend` = "Blend"
* `blend_subtitle` = "Collaborative playlist"
* `blend_members` = "%d members"
* `blend_invite` = "Invite to Blend"
* `blend_join` = "Join Blend"
* `blend_leave` = "Leave Blend"
* `blend_added_by` = "Added by %s"
* `blend_create_title` = "Make this a Blend?"
* `blend_create_body` = "Anyone with the link can add songs. You stay the owner."
* `blend_full_title` = "Blend is full"
* `blend_full_body` = "Blends are limited to 10 members for now."

---

## 2. Icon

**Do not invent a new glyph if you can reuse Material.** The app already uses `R.drawable.*` outlined icons. The closest existing are `group`, `people`, `diversity_3`, `share`.

**Proposed primary icon: `group` (two overlapping silhouettes) with a small music note badge.**

* **Library badge:** `group` at 14 dp, placed at the **bottom-end corner of the playlist thumbnail**, on a `MaterialTheme.colorScheme.primaryContainer` circular background (same pattern as the existing download/liked badges). This reuses the thumbnail badge slot and is visible in both grid and list view.
* **Playlist detail header icon:** `group` at 18 dp, inline with the "Blend • 3 members" subtitle, tinted `onSurfaceVariant`.
* **Empty-state / create-blend illustration:** `diversity_3` (three people) at 48 dp, centered above "Invite friends to add songs".
* **FAB / menu icon:** `person_add` for "Invite to Blend" action.

**Why not a custom `blend` vector yet:** A custom two-circle-overlap + note requires a new `blend.xml` vector and designer review. For v1, `group` is unambiguous and already in the APK. A custom icon can replace it in v1.1 without changing layout.

**Alternative if `group` is already used elsewhere:** Fallback to `share` with a `group` badge, or `library_music` + `group` small badge.

---

## 3. Library — how a Blend looks in the list

**File:** `app/src/main/kotlin/com/soundsphere/music/ui/screens/library/LibraryPlaylistsScreen.kt`

* **Grid and list items:** `LibraryPlaylistGridItem` / `LibraryPlaylistListItem` get a `isBlend: Boolean` param (derived from `PlaylistEntity.isCollaborative` or, for server playlists, `is_collaborative` from the `GET /user/playlists` union query). When true:
  * Thumbnail: bottom-end `group` badge (primaryContainer circle, 20 dp, icon 14 dp).
  * Title row: no change (playlist name stays).
  * Subtitle: replace `"%d songs"` with `"%d songs • Blend"` or, if member count is known, `"%d songs • Blend • %d members"`.
* **Filter chip:** No new chip for v1. Blends appear alongside normal playlists, ordered by `updated_at DESC` as today. A future "Blends only" filter can be a chip.

**Visual spec (grid, 2 columns, 160 dp card):**

```
┌─────────────────┐
│  thumbnail      │  ← 1:1, bottom-end badge (group, 20dp circle)
│                 │
├─────────────────┤
│ Road Trip 2026  │  ← title, max 2 lines
│ 12 songs • Blend│  ← subtitle, onSurfaceVariant, 12sp
└─────────────────┘
```

---

## 4. Inside the playlist — header, member strip, track attribution

**File:** `app/src/main/kotlin/com/soundsphere/music/ui/screens/playlist/LocalPlaylistScreen.kt` (and `PlaylistDetailScreen.kt` for server playlists)

### 4.1 Header (above the track list, below the existing title/cover row)

```
┌─────────────────────────────────────────┐
│  Road Trip 2026        [group 3]        │  ← title row
│  Blend • 3 members • 42 songs           │  ← subtitle row, onSurfaceVariant
│  [avatar][avatar][avatar]  [+ Invite]   │  ← member strip (see 4.2)
│  ─────────────────────────────────────  │
│  [Play]  [Shuffle]  [⋯ Blend settings]  │  ← existing play row, add overflow
└─────────────────────────────────────────┘
```

* **Subtitle:** `Blend • %d members • %d songs` — members from `GET /user/playlists/{id}/collaborators` (owner + collaborators count). Songs from `track_count`.
* **Blend settings overflow** (⋯) contains: `Invite to Blend`, `Member list`, `Leave Blend` (for members), `Remove member` (owner only), `Make private` (owner, sets `is_collaborative=false` — future).

### 4.2 Member strip — horizontal avatar row

* **Layout:** `LazyRow` with 32 dp circular avatars (`avatar_url` from `users` via collaborator list), overlapping by 8 dp (`Modifier.offset`), plus a trailing `+ Invite` pill button (`person_add` icon + "Invite").
* **Data source:** `GET /user/playlists/{id}/collaborators` → list of `{user_id, username, avatar_url, is_owner}`. Owner avatar first, then collaborators by `added_at`.
* **Tap avatar:** shows `username` tooltip; long-press maybe shows profile (future).
* **Tap `+ Invite`:** opens Invite sheet (see §5).
* **Overflow:** If >5 members, show `+N` circle (e.g. `+3`) that opens full member list bottom sheet.

### 4.3 Track list — "who added this" attribution

**This is required for v1.** Without it, a Blend is just a shared queue with no social loop.

* **Schema:** `playlist_tracks.added_by_user_id` (added in migration `007_blend_collaborative_playlists.sql` proposed in the investigation). App's `PlaylistSongMap` gets `addedByUserId: String?` column.
* **Row layout:** Existing `MediaMetadataListItem` (title, artist, thumbnail, duration) gets a **second subtitle line** when `isBlend == true`:

```
┌─────────────────────────────────────────────────┐
│ [thumb]  Title — Artist                         │
│          Album • 3:21                           │
│          Added by @martha • 2d ago  [avatar 16] │  ← new line, only for Blends
│                                          [⋯]    │
└─────────────────────────────────────────────────┘
```

* **Details:**
  * `Added by` uses `username` resolved from `added_by_user_id` via the collaborator list cache (no extra query per row; the detail endpoint already returns `added_by_user_id` per track — investigation § "What Already Exists" lists the `playlist_tracks(tracks(*))` select, which will be extended to include `added_by_user_id` and a join to `users(username, avatar_url)`).
  * Avatar: 16 dp circle at the end of the line, `avatar_url` if present else initials.
  * Time: `added_at` relative (e.g. `2d ago`) — already in `playlist_tracks.added_at`.
  * For tracks added before the `added_by_user_id` column existed (migration backfill), show no attribution line (fallback) — treat as "Added by owner".
  * Permissions: Any member can see attribution, but only owner can remove any track; members can remove tracks they added (future fine-grained; v1: any member can remove any track — simpler).

**File changes for attribution:**

* `app/src/main/kotlin/com/soundsphere/music/db/entities/PlaylistSongMap.kt` — add `addedByUserId: String? = null`
* `DatabaseDao` — migration, `playlist` detail query includes `added_by_user_id`
* `LocalPlaylistScreen.kt` — `itemsIndexed(tracks)` row composable gets `addedBy` param

---

## 5. Invite / join flow — reusing share_token

**Reuse the existing share_token mechanism** (investigation § "Sharing flow"). No new link format for v1.

### 5.1 Owner invites

1. Owner opens a playlist that is not yet a Blend → overflow → `Invite to Blend` (or `Make Blend` for first time).
2. First invite sets `is_collaborative = true` (via `PUT /user/playlists/{id}` with `is_collaborative=true` — new field on `PlaylistUpdateRequest`) and ensures `share_token` exists (via `POST /user/playlists/{id}/share` if null).
3. Share sheet: system share sheet with URL `https://api.soundsphere.name.ng/share/playlists/{token}` + copy button + QR (future). Text: "Invite to Blend: Road Trip 2026 — anyone with the link can add songs."
4. If playlist is already a Blend, the same action just shares the existing link (idempotent).

**File:** `PlaylistMenu.kt` — add `Material3MenuItemData` "Invite to Blend" (icon `person_add`) that calls `syncRepository.getPlaylistShareToken` then `shareLink`. New `BlendRepository.joinPlaylist(token)` is not called by owner.

### 5.2 Recipient joins

1. Recipient taps link: `soundsphere://p/{token}` → `SharedPlaylistScreen.kt` (already handles `shared_playlist/{token}` via `SyncService.getSharedPlaylist(token)`).
2. Currently shows read-only preview (name, cover, owner, track list, Play button). **For Blends, change:** if `playlist.is_collaborative == true` (new field on the public `GET /share/playlists/{token}` response), show a primary `Join Blend` button (icon `group_add`, label "Join Blend") instead of just Play. Below it, secondary "Preview" (read-only).
3. Tap `Join Blend` → `POST /share/playlists/{token}/join` (new authenticated endpoint, investigation Phase 1 #4) with Bearer JWT → inserts into `playlist_collaborators`, returns full playlist (201 or 200 if already member). App then `SyncRepository.pullPlaylists()` or directly inserts the returned playlist into Room as a Blend (with `isCollaborative=true`).
4. After join, navigate to `local_playlist/{id}` (the newly synced Blend) with a snackbar "Joined Blend: Road Trip 2026".
5. If not logged in, tapping `Join Blend` routes to login, then retries join.

**Deep link handling:** `MainActivity.kt` already routes `soundsphere` scheme and `soundsphere.name.ng` host — no change, just the screen's button logic branches on `is_collaborative`.

### 5.3 Leaving / removing

* **Leave:** Member overflow → `Leave Blend` → `DELETE /user/playlists/{id}/collaborators/{self}` (or dedicated `POST /share/playlists/{token}/leave`) → local Room delete of that playlist (or mark as not-collaborative). No cascade delete of tracks.
* **Remove member (owner only):** Member list bottom sheet → long-press member → `Remove from Blend` → `DELETE /user/playlists/{id}/collaborators/{userId}`.
* **Delete playlist (owner only):** Existing `DELETE /user/playlists/{id}` already cascades `playlist_tracks` and `playlist_collaborators` — all members lose access immediately. Confirm dialog should warn: "Delete this Blend? All %d members will lose access."

---

## 6. Edge cases and states

| State | UI |
|-------|----|
| Empty Blend (no tracks) | Same empty state as normal playlist, but subtitle says "Blend • No songs yet — be the first to add". FAB "Add songs" prominent. |
| Full Blend (10 members) | Invite button disabled, subtitle "Blend • 10/10 members". Share still works but server returns 409 "Blend is full" on join attempt → snackbar. |
| Offline | Member avatars and attribution from Room cache still show; add/remove queues locally and syncs on reconnect via existing `SyncRepository` retry. Invite/join requires online — button disabled with "Online only" tooltip. |
| Not a Blend (normal playlist) | No badge, no member strip, no attribution line. "Invite to Blend" in overflow converts it to Blend on first use. |
| Legacy tracks (added before `added_by_user_id`) | No "Added by" line (or "Added by owner" fallback). After migration, new adds always have attribution. |

---

## 7. What this plan reuses vs what is new

**Reuses (no new infra):**
* `share_token` generation and `GET /share/playlists/{token}` (existing)
* `playlist_collaborators` table and `is_collaborative` flag (already in DB, 0 rows)
* `SyncRepository` / `SyncService` pull/push pattern (extend `GET /user/playlists` union query)
* `PlaylistSongMap` + `LocalPlaylistScreen` track list (add one column + one subtitle line)

**New for v1:**
* 1 migration: `added_by_user_id` + unique constraint (investigation § "Schema Changes")
* 3 backend endpoints: `POST /share/playlists/{token}/join`, `GET /user/playlists/{id}/collaborators`, `DELETE .../collaborators/{userId}` (+ update `PUT /playlists/{id}` to set `is_collaborative`, and `GET /user/playlists` union)
* `_get_accessible_playlist` helper (investigation § "Permission Changes") for add/remove/detail on Blends
* `GET /share/playlists/{token}` response adds `is_collaborative: bool` and per-track `added_by: {username, avatar_url}`
* 2-3 new composables: member strip, attribution subtitle, invite sheet / join button branch
* String additions only in `app/src/main/res/values/soundsphere_strings.xml`

---

## 8. Build order (when you say go)

1. **Backend first** (migration + `_get_accessible_playlist` + union list + join + collaborators) — app depends on the new `is_collaborative` field.
2. **App DB** — `PlaylistSongMap.addedByUserId` + Room migration.
3. **Sync layer** — `SyncService` join/collaborators calls, `SyncRepository` union pull, attribution parsing.
4. **UI** — Library badge, detail header + member strip, attribution line, invite share sheet, `SharedPlaylistScreen` Join branch, deep link.
5. **Polish** — empty/full/offline states, leave/remove, delete warning.

Estimated effort: ~1 week (matches investigation § "Implementation Plan").

