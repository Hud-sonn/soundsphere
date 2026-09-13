---v1.3.0
# Soundsphere v1.3.0 — Blend: Playlists You Build Together (Beta)

The headline feature this release is Blend — shared playlists where you and your friends add songs together. **Blend is currently in beta** — we're actively fixing things and polishing the experience, so you might see a few rough edges. We'd love your feedback.

# What's new

**Blend — collaborative playlists**
- Create a Blend from the Library tab (tap the three-rings icon in the bottom-right corner)
- Give your Blend a name, then choose to start fresh or pull in songs you've been listening to
- Invite friends by sharing a link — they tap to join
- See who added each song — every track shows the contributor's name
- Browse a contribution bar that shows who added the most music
- Filter the tracklist to see just one person's picks
- Remove songs you don't want in the Blend
- Leave the Blend anytime from the Invite screen
- You can create up to 3 Blends on the free plan, with up to 10 members and 500 songs each

**AI Curator — a full-screen experience**
- The "Create with AI" button now opens a dedicated screen instead of a small popup
- Write a short description of the vibe you want (e.g. "late-night rainy drive in Tokyo")
- Use the "Surprise Me" button for random inspiration
- Pick from mood seed chips to get started quickly

# What's fixed
- Playlists you create from a Blend are now marked as collaborative — they show the Blend icon and sync correctly
- Songs no longer get added to a Blend twice when multiple people are editing at the same time
- The Blend invite screen now shows a real count of how many members are in the playlist
- Track attribution (who added what) now shows up correctly for songs added before the feature was ready
- Cover art syncs properly when the playlist owner and a joined member both make changes
- Song order now stays in sync across devices
- Joining a Blend no longer causes a black screen
- Playback cache now correctly marks downloaded songs and handles repeat/end-of-queue states
- Search now looks through song titles, artist names, and album names at the same time
- The Android Auto settings screen has a slider to control how many songs appear in search results

# Behind the scenes
- Improved how the app talks to the server when syncing playlists — errors in one part no longer break the whole sync
- The app now falls back to a backup server if the main one is slow or unreachable
- Better memory handling during builds (no change for users)
- Added unit tests for playlist sync logic, cache behavior, and server communication

## Downloads
- FOSS: `Soundsphere.apk` (universal) and `app-<arch>-release.apk`
- With Google Cast (GMS): `Soundsphere-with-Google-Cast.apk` (universal) and `app-<arch>-with-Google-Cast.apk`

---v1.2.0
# Soundsphere v1.2.0
The biggest update since the Soundsphere rebrand. This release is built around what you asked for: a real profile with your own avatar, more lyrics sources, a look you can make your own, and a calmer, more reliable experience.

# What's new
- Your own Soundsphere profile: pick a username and an avatar photo that follows your account everywhere in the app
- Announcements inside the app: new features, fixes and news now reach you directly, no need to check Telegram
- Wallpaper theming: pick any photo and use it as the app background, with the app's colors generated from it automatically
- More ways to choose your look: hand-picked color schemes, curated color combos, and the rounder Material U look
- Lyrics from two new sources (SimpMusic and Unison), with toggles and a priority list in settings
- Share playlists with a link that opens straight in the app and previews nicely in chat apps
- Download button added to the classic player layout
- A redesigned sidebar with your profile at the top
- Clear loading, error and empty states everywhere: screens now tell you what went wrong and let you retry instead of spinning forever

# What's fixed
- Changing the wallpaper actually changes the background now
- Now Playing controls sit higher and are easier to reach

## Other improvements
- Playlist thumbnails now show a collage of the songs' covers
- The app feels slightly more compact out of the box (95% display density by default)
- Better error messages throughout the app
- Behind the scenes: more secure data storage, a live activity feed for the team, and automatic fallback if the backend is unreachable

## Downloads
- FOSS: `Soundsphere.apk` (universal) and `app-<arch>-release.apk`
- With Google Cast (GMS): `Soundsphere-with-Google-Cast.apk` (universal) and `app-<arch>-with-Google-Cast.apk`

---v1.1.4
# Soundsphere v1.1.4
Update-check polish. Checking for updates no longer shows a cryptic error when GitHub's API is unavailable — it degrades gracefully and says when you're up to date.

# Major changes
- "No value for tag name" error gone: GitHub API errors (rate limits, no releases) are now handled properly instead of surfacing raw parse exceptions
- The updater now shows "You're up to date" when no newer version exists
- Changelog loading is leaner: it stops fetching releases once it passes your current version, cutting unauthenticated GitHub API calls from up to ten to one

## Downloads
- FOSS: `Soundsphere.apk` (universal) and `app-<arch>-release.apk`
- With Google Cast (GMS): `Soundsphere-with-Google-Cast.apk` (universal) and `app-<arch>-with-Google-Cast.apk`

---v1.1.3
# Soundsphere v1.1.3
Polish release. The splash now holds until Home is fully loaded, playback stops when you sign out, and the About screen reflects where Soundsphere is really from.

# Major changes
- Splash screen now stays on screen through Home's initial load (signed-in launches), so the app opens straight into content instead of a blank flash
- Playback now stops and the queue is cleared when you log out, so music doesn't keep playing after the session ends
- Tapping "ready to install" no longer leaves the installer behind: the install prompt keeps focus after the app moves to the background
- About screen updated: "Proudly built in Nigeria" instead of the upstream Palestine banner, a GPL-3.0 license badge next to the version, WhatsApp and email contact buttons, and a new "Report an Issue" section for direct support

## Downloads
- FOSS: `Soundsphere.apk` (universal) and `app-<arch>-release.apk`
- With Google Cast (GMS): `Soundsphere-with-Google-Cast.apk` (universal) and `app-<arch>-with-Google-Cast.apk`

---v1.1.2
# Soundsphere v1.1.2
Bug-fix release. Tapping the update notification now hands the downloaded APK to the installer from the foreground, where Android allows it — previously the tap was silently swallowed on Android 10+.

# Major changes
- Fixed "ready to install" notification doing nothing: the installer now launches through the app itself, so the update actually installs after downloading
- Added a background keep-alive worker that pings the backend health endpoint every 15 minutes, so the free Render service never spins down and first login stays fast

## Downloads
- FOSS: `Soundsphere.apk` (universal) and `app-<arch>-release.apk`
- With Google Cast (GMS): `Soundsphere-with-Google-Cast.apk` (universal) and `app-<arch>-with-Google-Cast.apk`

---v1.1.1
# Soundsphere v1.1.1
Bug-fix release. The in-app update download previously crashed on launch, so updates never arrived; that flow is repaired and the splash now closes with a branded animation.

# Major changes
- Fixed the in-app update download crashing at start ("foregroundServiceType" mismatch) on Android 11+, so downloads now complete with a progress notification and a "ready to install" prompt
- Splash screen now fades, zooms, and exits when the app opens — on both signed-in and signed-out launches
- About screen rebranded to Hudson Dev: updated social links, portfolio, credits, and a GPL attribution to the original Metrolist project

## Other improvements
- Update progress notifications no longer crash on Android 13+ when notification permission is missing
- CI now generates a debug keystore on the runner when the repository secret is absent, unblocking debug builds

## Downloads
- FOSS: `Soundsphere.apk` (universal) and `app-<arch>-release.apk`
- With Google Cast (GMS): `Soundsphere-with-Google-Cast.apk` (universal) and `app-<arch>-with-Google-Cast.apk`

---v1.1
# Soundsphere v1.1
First release under the Soundsphere project. Forked from Metrolist with a fresh brand, a new account system, and a batch of stability fixes.

# Major changes
- New Soundsphere account system: register, email verification, login, and password reset, with an encrypted local session (replaces Discord-based auth)
- Splash and onboarding screens rebuilt around the Soundsphere brand
- Fixed a startup crash on Android 11 and below caused by the launcher icon being loaded as a splash bitmap; splash now uses a dedicated vector logo
- Graceful fallback to plain SharedPreferences when EncryptedSharedPreferences is unavailable, so login no longer breaks on devices without a Keystore
- Forgot-password flow now navigates to the reset screen only after the reset email is actually sent

## Other improvements
- Smoothed lyrics auto-scroll centering by scaling the scroll animation to the travel distance
- Faster home screen load after login

## Downloads
- FOSS: `Soundsphere.apk` (universal) and `app-<arch>-release.apk`
- With Google Cast (GMS): `Soundsphere-with-Google-Cast.apk` (universal) and `app-<arch>-with-Google-Cast.apk`