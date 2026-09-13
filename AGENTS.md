# Working with Soundsphere as an AI agent

**Date:** 2026-09-12
**Status:** Live
**Why:** Added redesign-completeness rule (mock section inventory before coding) — every MD must start with Date/Status/Why and HTML docs are served from backend /docs.

Soundsphere is a 3rd party YouTube Music client written in Kotlin. It follows material 3 design guidelines closely.

## Rules for working on the project

1. Always pull the latest changes from `main` before starting your work to minimize merge conflicts.
2. Commit names should be clear and follow the format: `type(scope): short description`. For example: `feat(ui): add dark mode support`. Including the scope is optional.
3. All string edits should be made to the `Soundsphere/app/src/main/res/values/soundsphere_strings.xml` file, NOT `Soundsphere/app/src/main/res/values/strings.xml`. Do not touch other `strings.xml` or `soundsphere_strings.xml` files in the project. ONLY edit the default (English) `soundsphere_strings.xml` file, DO NOT EDIT OTHER LANGUAGES.
4. You are to follow best practices for Kotlin and Android development.
5. DO NOT EDIT THE APP'S DATABASE SCHEMA.
6. **Record every change in `CHANGES.md`.** Whenever you change, add, or fix anything (code, backend, website, strings, config), add a short concrete entry to `CHANGES.md` before finishing the task — area, what changed, and whether it is `pushed` or `local`/uncommitted. Newest date block goes on top. This file is the running record used to assemble release notes later; it is the one markdown file you MUST keep up to date.

## AI-only guidelines

1. You are strictly prohibited from making ANY changes to the readme/markdown files, including this one — with one exception: `CHANGES.md`, which MUST be updated for every change per the rules above. This is to ensure that the documentation remains accurate and consistent for all contributors.
   - **Date rule:** Every markdown file you create or modify (investigations, plans, docs, `CHANGES.md` entries, `INVESTIGATION_*.md`, `BLEND_*.md`, `SECURITY_*.md`, etc.) **must** start with a header containing `Date: YYYY-MM-DD`, `Status`, and `Why/Reason` (one line explaining why it was created/updated). Update the `Date` on every edit. HTML docs generated from these MDs must show the same date/why in their header and be viewable only from the backend domain (`/docs`).
2. Unless explicitly requested, you are not allowed to commit, push, or merge any changes to any branch. If you are explicitly requested and authorized to commit/push/merge, you have the right to do so; the responsibility then lies with the author who requested it.
   - You should absolutely NOT use any commands that would modify the git history, do force pushes (except for rebases on your own branch), or delete branches without explicit instructions from a human.
3. Always follow the guidelines and instructions provided by human contributors.
4. Ensure the absolutely highest code quality in all contributions, including proper formatting, clear variable naming, and comprehensive comments where necessary.
5. Comments should be added only for complex logic or non-obvious code. Avoid redundant comments that simply restate what the code does.
6. Prioritize performance, battery efficiency, and maintainability in all code contributions. Always consider the impact of your changes on the overall user experience and app performance.
7. If you have any doubts ask a human contributor. Never make assumptions about the requirements or implementation details without clarification.
8. If you do not test your changes using the instructions in the next section, you will be faced with reprimands from human contributors and may be asked to redo your work. Always ensure that you test your changes thoroughly before asking for a final review.
9. You are absolutely **not allowed to bump the version** of the app in ANY way. Version bumps are only done by the core development team after manual review.

## Documentation dating

Every markdown file produced for investigation, audit, or planning purposes (investigation reports, audit reports, architecture decisions) must include, at the top of the file:
- The date the file was created
- The date of the most recent change, if edited after creation
- A one-line summary of what changed and why, for every edit after the initial version (append, don't overwrite — keep a running changelog at the top of the file itself)

This applies retroactively when editing any existing investigation/audit doc: if you modify one, add a dated entry noting what changed, do not silently update it with no record of the prior state.

## Row Level Security — apply to all future tables, not just audited ones

Any new table added to this project, in either Supabase project, must be checked against the following rule before shipping: if a table has an `authenticated`-role UPDATE or INSERT/UPSERT policy scoped to "the user's own row" (e.g. `user_id = auth.uid()` or `id = auth.uid()`), every column in that table must be reviewed for whether it represents a privileged, non-user-editable value (limits, counts, roles, flags, tiers, tokens, verification status, anything the user should not be able to set themselves).

If any such column exists on a table with a user-writable policy, one of the following must be applied before the table is used in production — do not ship a user-writable table containing a privileged column with no protection, even temporarily:
1. Move the privileged column to its own separate table with no `authenticated` policy at all (`service_role`-only writes), or
2. Add a `BEFORE UPDATE`/`INSERT` trigger (matching the pattern in `prevent_privileged_user_column_changes` / `prevent_privileged_playlist_column_changes`) that rejects any attempt to change that column via the `authenticated` role

This check must be performed as a standard step whenever a new table is created, not only during a dedicated security audit — treat it as part of the table's initial design, the same way choosing a primary key or foreign key is.

## Account-level caps — server-enforced, premium raises them

All usage caps MUST be enforced account-level (keyed by `user_id` on the backend, never by device, install, or app cache) so clearing app data / reinstalling / switching devices cannot reset them. Client-side checks are UX hints only; the backend is the real lock (same principle as premium gating — rooted clients bypass UI).

Current caps (free tier) live in `backend-auth/routers/user.py` (`_PLAYLIST_SYNC_LIMIT = 20`, `_LIKED_LIMIT = 2000`, `_FOLLOWED_ARTIST_LIMIT = 200`, `_PLAYLIST_TRACK_LIMIT = 500`, history prune 500, AI generation 2/day):
- **Blend (collaborative) playlists: 3 per user** on free. Count owned playlists with `is_collaborative = true`; reject creation above the cap with 409.
- When Premium launches, EVERY cap gets a higher premium value (Blend 3 → 10; playlists, liked, follows, tracks, history, AI quota all raised). Tier source is the `subscriptions` table (`expires_at > now()`), never `users.role` (trigger-protected) and never anything the client asserts. Until billing ships, everyone is free tier — do not invent ad-hoc exceptions.

## Architectural rule — no direct Supabase writes with backend JWT

Confirm no current or planned code path will ever cause the app to send `Authorization: Bearer <backend-JWT>` directly to `*.supabase.co` with the `anon` key. Document this explicitly as an architectural rule rather than relying on it remaining true by coincidence. If Blend or Listen Together's real-time sync needs ever introduce a `supabase-js`/`realtime` client on the app side, that client must use its own, separately-issued Supabase Auth session — never the backend's custom JWT — precisely so the `role: authenticated` mismatch that's accidentally protecting these tables today isn't silently removed by a future feature.

## Redesign completeness — mock section inventory before calling it done

When the user orders a redesign from a mock/design file: FIRST enumerate every section of the mock (header, nav, hero, artwork, cards, rows, lists, buttons, badges, footers) and mark each KEEP / CUT / ADAPT with a one-line reason — present that strip list before coding whenever the user asked for strip-first review. THEN code. The body (cards, rows, list items) is part of the redesign: converting only chrome/headers/tiles while leaving the old body structure is an INCOMPLETE redesign and must never be reported as finished. Verify each mapped element exists in the edited screen before reporting done, and record the section mapping (with cut reasons) in the design doc. Applies to every coming screen in a design series, not just the first.

## Upstream tracking — watch external InnerTubeX and source repos weekly

Metrolist has moved its `innertube` to the external artifact `InnerTubeX` (`com.github.MetrolistGroup:InnerTubeX`) while Soundsphere keeps a vendored `innertube` module (`innertube/src/main/kotlin/com/soundsphere/innertube`). Any change in `InnerTubeX` can contain parsing, stream-source, or auth fixes that Soundsphere needs. Treat `https://github.com/MetrolistGroup/Metrolist` (and its `gradle/libs.versions.toml` `innertubex` version) and `https://github.com/MetrolistGroup/Metrolist.git` commits that touch `innertube`/`InnerTubeX` as an upstream to watch.

Check at least once a week (and before any release):

1. `git -C /tmp/metrolist fetch origin` + `git log --oneline --since="1 week ago"` on that clone (kept at `/tmp/metrolist` on the dev machine) and compare to `git log --oneline --since="1 week ago"` on `soundsphere`.
2. `gradle/libs.versions.toml` `innertubex` vs local `innertube` — if Metrolist bumped `innertubex`, inspect the `InnerTubeX` release notes and the `innertube/src` diff in that Metrolist commit range for `YouTubeQueue`, `InnerTube`, `YouTube` parsing/stream changes that need to be cherry-picked into the local `innertube` module.
3. The same weekly sweep must also cover the other source repos that were forked: `https://github.com/MetrolistGroup/Metrolist.git` commits beyond `InnerTubeX` (e.g. `SyncUtils`, `UploadManager`, `Queue`, `Player`) — use `git log --oneline --since="1 week ago" | grep -v "Translated using Weblate"` as the triage list.

Do not skip the translation batch (`Translated using Weblate`) when strings are allowed — those are safe `values-*/strings.xml` additions with no `metrolist` mention (commit message says `Translated using Weblate`, the diff is just `<string name="…">`). When `AGENTS.md:3` would otherwise block non-English edits, the weekly upstream sweep is an explicit exception for those `values-*/` translation commits.

## Building and testing your changes

1. After making changes to the code, you should build the app to ensure that there are no compilation errors. Use the following command from the root directory of the project:

```bash
./gradlew :app:assembleFossDebug
```

2. If the build is not successful, review the error messages, fix the issues in your code, and try building again.
3. Once the build is successful, you can test your changes on an emulator or a physical device. Install the generated APK located at `app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk` and ask a human for help testing the specific features you worked on.

## RAM-constrained builds (development machine)

The dev machine has only ~5.6 GiB total RAM (~2.8 GiB free). The default `gradle.properties` requests 4 GiB heaps for both the Gradle and Kotlin daemons, which can OOM/thrash. Before EVERY build:

1. Kill the Gradle and Kotlin daemons to free their memory:

```bash
./gradlew --stop
pkill -f kotlin.daemon
```

2. Temporarily constrain the heaps and workers in `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048M -Dkotlin.daemon.jvm.options\="-Xmx1536M" -XX:+UseParallelGC
org.gradle.workers.max=2
```

3. Build with the worker cap (note: it is slow, expect 4-15 min):

```bash
./gradlew :app:assembleFossDebug --max-workers=2
```

4. RESTORE the original values in `gradle.properties` after the build finishes (verify with `git diff --stat gradle.properties` that no diff remains) — do not commit the constrained values.

## Backend (FastAPI) local testing

The production backend's `ALLOWED_HOSTS` deliberately contains **only** `api.soundsphere.name.ng` and `soundsphere-auth.onrender.com` — never `localhost`. Before running the backend locally, remember to add `localhost` via the env var or every request will get a 400, e.g.:

```bash
ALLOWED_HOSTS="localhost,api.soundsphere.name.ng,soundsphere-auth.onrender.com" uvicorn main:app --port 8000
```

Never include `localhost`/`127.0.0.1` in the production `ALLOWED_HOSTS` (it would defeat the host-based protection). The app's debug builds can point at a local backend with `AUTH_DEV_BASE_URL` in `local.properties` (e.g. `AUTH_DEV_BASE_URL=http://10.0.2.2:8000`); the production fallback host remains the onrender URL automatically.
