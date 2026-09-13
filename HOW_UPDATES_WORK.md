# How Soundsphere Updates Work

**Date:** 2026-09-13
**Status:** Complete
**Why:** Research how the in-app update mechanism works end-to-end, from git tag to app receiving the update.

## Overview

Soundsphere has a GitHub-based update system. Pushing a version tag triggers a release workflow that builds signed APKs, publishes them as a GitHub Release, and the in-app updater picks them up automatically.

## End-to-End Flow

### Step 1: Tag push triggers release workflow

When you push a tag like `v1.2.2-beta`, the release workflow (`.github/workflows/release.yml`) runs:

1. Checks out the code
2. Sets up JDK 21 + Gradle
3. Decodes the release keystore from secrets
4. Builds `assembleFossRelease` + `assembleGmsRelease` (both signed)
5. Stages APKs as:
   - `Soundsphere.apk` (FOSS — universal, no Google Cast)
   - `Soundsphere-with-Google-Cast.apk` (GMS — universal, with Cast)
6. Extracts release notes from `changelog.md` using `parse_changelog.sh`
7. Creates a GitHub Release with tag name as title + APKs as assets

### Step 2: In-app updater checks GitHub API

`Updater.kt` queries `https://api.github.com/repos/Hud-sonn/soundsphere/releases/latest` on:
- App startup (if `CheckForUpdatesKey` is enabled — it is by default)
- Manual check in Settings → Updater

The updater parses:
- `tag_name` → used for version comparison
- `name` (release title) → shown as `versionName` in the UI
- `body` → shown as release notes
- `assets` → parsed by filename to find the correct APK variant

### Step 3: Version comparison

`compareVersions(v1, v2)` in `Updater.kt`:
- Strips the `v` prefix
- Splits by `.`
- Parses each part as `Int` (non-numeric parts become `0`)
- Compares left to right

**Important with beta tags:** `-beta` suffix causes the patch number to parse as `0`.
- `1.2.2-beta` → `[1, 2, 0]`
- `1.2.1` → `[1, 2, 1]`
- Result: `1.2.2-beta` is treated as `1.2.0`, which is **less than** `1.2.1`

This means **beta tags will NOT auto-update existing stable users** — which is correct beta behavior. Only users who manually install the beta APK will run it.

### Step 4: Asset matching

`parseAssets()` in `Updater.kt` matches APKs by exact filename:
- `Soundsphere.apk` → FOSS universal
- `Soundsphere-with-Google-Cast.apk` → GMS universal
- `app-<arch>-release.apk` → FOSS per-architecture
- `app-<arch>-with-Google-Cast.apk` → GMS per-architecture

The updater picks the APK matching the user's current variant (FOSS vs GMS) and architecture.

### Step 5: Download and install

`AppUpdateDownloadJob.kt` (WorkManager foreground service):
1. Downloads APK from GitHub release asset URL to `externalCacheDir`
2. Reports progress via notification + Settings UI
3. On completion, posts "ready to install" notification
4. User taps notification → opens `MainActivity` → hands APK to Android installer via `FileProvider`
5. No auto-install — user must confirm

### Step 6: Changelog extraction

`parse_changelog.sh` extracts release notes from `changelog.md`:
- Looks for `---vX.Y.Z` section headers
- Extracts content until the next `---v` header
- Falls back to auto-generated notes from git commits if section not found

## Workflows Overview

| Workflow | Trigger | What it does |
|---|---|---|
| `release.yml` | Tag push `v*.*.*` | Builds signed release APKs, creates GitHub Release |
| `build.yml` | Branch push | Builds FOSS + GMS release APKs as artifacts |
| `build_quick.yml` | Branch push | Builds quick FOSS release APK (no lint) |
| `build_pr.yml` | Pull request | Builds FOSS debug APK for PR testing |
| `sync-player-configs.yml` | Cron (twice daily) | Syncs player configs from upstream Metrolist |

## How to Push an Update So the App Receives It

1. Bump `versionCode` + `versionName` in `app/build.gradle.kts`
2. Update `changelog.md` with a `---vX.Y.Z` section
3. Commit + push to `main`
4. Create and push a tag: `git tag -a vX.Y.Z -m "description" && git push origin vX.Y.Z`
5. Release workflow runs automatically → GitHub Release created → APKs published
6. Users on the next app launch see the update notification / can check in Settings

**For beta releases:** Use a tag like `v1.2.2-beta`. Existing stable users will NOT auto-update (version comparison treats `-beta` as patch 0). Only users who manually install the beta will run it.

## Key Files

- `.github/workflows/release.yml` — release workflow
- `.github/scripts/parse_changelog.sh` — changelog extraction
- `app/src/main/kotlin/com/soundsphere/music/utils/Updater.kt` — version check + API calls
- `app/src/main/kotlin/com/soundsphere/music/utils/AppUpdateDownloadJob.kt` — download + install
- `app/src/main/kotlin/com/soundsphere/music/ui/screens/settings/UpdaterSettings.kt` — update UI
- `app/build.gradle.kts:122-123` — versionCode + versionName
- `changelog.md` — release notes (section headers must match `---vX.Y.Z`)

## Current Status

- Tag `v1.2.2-beta` pushed, release workflow running
- 3 workflows triggered (Release, Build APKs, Quick Test Build) — all standard, no action needed
- Version bumped to `1.2.2-beta` (versionCode 12)
- 11 clean SoundSphere tags on remote (v1.1 through v1.2.2-beta), all Metrolist tags removed
