# Soundsphere Design System Plan — Material U + Liquid Glass

**Date:** 2026-08-14 · **Status:** Proposal (no code yet) · **Applies to:** Android app (Compose, Material 3)

## 1. Current state (what we have today)

- **Theme layer:** `ui/theme/Theme.kt` — Material 3 with a seed-color system:
  - Default = fixed **Earthy Tones** palettes (dark `#141312` background, `#5E503F` stone primary, `#C6AC8F` khaki accent, cream text `#EAE0D5`).
  - Any other user-chosen seed → `materialKolor` `rememberDynamicColorScheme` (SPEC_2025, TonalSpot), works on all API levels.
  - Optional "pure black" OLED mode; light/dark follow the system.
- **Shapes:** `EarthyShapes` 4/8/12/16/24 dp + pill.
- **Player:** already dynamic — `PlayerColorExtractor` + `PlayerSliderColors` sample artwork colors live on the player screen.
- **Auth screens:** own accent (`AuthColors.kt`) that slightly deviates from the theme.
- **Screens today:** Home, Search, Library (songs/albums/artists/playlists/podcasts/mixes), Player (full + mini), Playlists (local/online/cache/auto), Shared playlist, Artist (overview/albums/songs/items), Settings (incl. theme, integrations), Equalizer (+ wizard), Podcast, Song recognition, Wrapped, Auth (splash/login/register/OTP/forgot/reset), Sidebar, menus, bottom sheets.

## 2. Direction (two-tier, from `docs/design-options.md`)

- **Material U (option A)** — ship as the new default. Evolution of the current Earthy M3: dynamic color, rounder surfaces (larger radius on big containers, pill buttons), softer shadows/tonal contrast, more generous spacing, artwork-driven accents where the player already leads.
- **Liquid Glass (option B)** — opt-in experimental theme (Settings → Theme → "Liquid Glass"): translucent, blurred, tinted surfaces over artwork (scrim + backdrop blur), glass panels, refined highlights. Marked experimental; uses Android 12+ `RenderEffect` blur or Compose blur modifier with graceful fallback to solid panels.

### 2.1 Do we keep the current theme color? — YES (with one tweak)

The seed-color system is the app's identity and already user-controlled; replacing it would be a regression. Plan:

- **Keep** `DefaultThemeColor = #5E503F` (Earthy stone) as the default seed — it is the brand color, already used across marketing/website/backend OG pages (`#0A0908`/`#C6AC8F`/`#EAE0D5`).
- **Keep** the Earthy fixed palettes as the default scheme, but refresh them to Material U: slightly warmer neutrals, one tonal step deeper for surfaces so white text keeps contrast.
- **Keep** `materialKolor` dynamic schemes for non-default seeds.
- **Add** "Material U" and "Liquid Glass" as *variants that reuse the seed* — color stays, surface treatment changes. `Theme.kt` gains a `ThemeVariant` enum (`EARTHY_U`, `LIQUID_GLASS`) resolved from a new settings pref (default `EARTHY_U`).
- Extend `PlayerColorExtractor` usage beyond the player: home hero + now-playing mini card tint from current artwork (only on the player-centric surfaces, never the whole app — battery/contrast safe).

## 3. What changes page by page

### 3.1 Theme & global tokens (`ui/theme/`)
- `Theme.kt`: `ThemeVariant` enum; new `MaterialUDark/Light` palettes (derived from Earthy with new surface tones); `LiquidGlass` surfaces (alpha scrim layers over artwork); `EarthyShapes` → `MaterialUShapes` (sm 8 / md 12 / lg 20 / xl 28 / pill).
- `Typography.kt`: keep family, bump display/headline weights (700) for Material U; keep type scale (no layout breakage).
- New `ui/theme/Glass.kt`: blur + tint modifiers, `GlassSurface` composable with fallback.
- `App.kt`: read `ThemeVariant` pref, pass to `SoundsphereTheme`, expose state for live switching.

### 3.2 Sidebar (`ui/component/Sidebar.kt`)
- NavigationDrawer surface → elevated rounded panel (M3 drawer with `drawerShape = MaterialUShapes.large`), section headers in small caps, active item = tonal pill with seed-tinted container.
- Profile header: add a subtle artwork-bleed gradient behind avatar (Material U).
- No behavior changes (items already fixed).

### 3.3 Home
- Hero section: rounded 28 dp gradient card seeded from first-recommendation artwork (existing `extractGradientColors`), title on the card.
- Section chips (`ChipsRow`): pill shapes, tonal selected state (already mostly there — align to new shapes).
- Cards/rows: 16→20 dp radius on artwork cards, `surfaceContainerHigh` cards.

### 3.4 Search
- Search bar → full pill shape with tonal container; suggestion chips already pill.
- Results: align rows with new spacing scale; keep dense list style (search must stay fast).

### 3.5 Library (6 tabs)
- Keep tab structure; tab indicator → seed-toned pill (M3 primary container).
- `LibraryMixScreen`: mix cards → 28 dp radius + gradient bleed (artwork-consistent).
- List rows: unify to 64 dp with 20 dp artwork radius.

### 3.6 Player (full + mini) — biggest visual win, already artwork-driven
- Full player: keep artwork gradient system; add **glass detail panel** (buttons row/slider container = scrim + blur over artwork, `GlassSurface`).
- Slider: `PlayerSliderColors` stays, track gets thicker (6 dp), thumb glow.
- Buttons (play/pause/skip): 56 dp tonal circles, play button = primary color with icon in `onPrimary`.
- Mini player: rounded 20 dp floating bar over content with backdrop tint from artwork (subtle, blurred).

### 3.7 Playlists & Shared playlist
- Playlist header: artwork-cover with 28 dp radius; gradient scrim into background.
- `SharedPlaylistScreen`: same treatment; fallback cover (existing `_ARTWORK_FALLBACK` style) gets tonal placeholder + icon.

### 3.8 Artist
- Header: large radius artwork + gradient bleed; chips for Songs/Albums → pill segmented control.

### 3.9 Settings
- Preference rows → grouped `surfaceContainer` cards with 20 dp radius, section separators removed.
- New **Theme section**: existing color seeds + new "Look" picker (Earthy U / Liquid Glass) + preview swatches.
- Buttons: filled = pill (M3 `Button` with `shape = CircleShape`), tonal actions same; outlined stays but radius matches.

### 3.10 Auth
- Replace `AuthColors.kt` divergence: use theme seed directly (consistent branding with website).
- Card → 28 dp radius `GlassSurface` (subtle blur over splash art) in Liquid Glass; solid tonal card in Material U.

### 3.11 Bottom sheets & menus (`ui/menu`, queues, share sheet)
- Sheets: top corners 28 dp, drag handle 4 dp tonal; menu items keep 48 dp targets.
- UpdateChangelogSheet: same sheet treatment.

### 3.12 Equalizer, Wrapped, Recognition, Podcast
- Equalizer: keep technical readout; glass panel behind faders; wizard screens match auth card.
- Wrapped: already special-cased; only shape/type tokens inherit — no redesign.
- Recognition/Podcast: card/row token updates only (low priority).

## 4. Button specification (Material U)

| Button | Shape | Size | Fill |
|---|---|---|---|
| Primary (play, save, sign in) | Pill (`CircleShape`) | 48 dp h, min 88 dp w | `primary`, text `onPrimary` |
| Tonal (chips, tab actions) | Pill | 40 dp h | `secondaryContainer`/`primaryContainer` |
| Icon (player, menus) | Circle 48 dp (player 56 dp) | 48/56 | `surfaceContainerHighest` scrim or `primary` for play |
| Outlined / text | Pill / none | 40–48 dp | transparent, `outline` border |

All reachable at 48 dp touch targets; no layout size changes to rows/lists.

## 5. Theme color answer (summary)

- **Yes, we use the current theme color** — `#5E503F` stays the default seed; dynamic seed selection stays; brand palette unchanged (`#0A0908`, `#5E503F`, `#C6AC8F`, `#EAE0D5`).
- Material U = new *surfaces* (tones/radius/spacing) on the same color system.
- Liquid Glass = new *surface material* (blur/tint) on the same color system.
- Artwork-derived color stays player-scoped (battery/contrast-safe), plus home hero gradient.

## 6. Implementation plan (phases)

**Phase 0 — Foundation (one commit, no visual change):** `ThemeVariant` enum + pref plumbing in `App.kt`/Settings; `MaterialUShapes`; tokens extracted to `ui/theme/`.
- Verify: build + install, theme toggle works, zero regressions.

**Phase 1 — Material U default:** palettes refresh in `Theme.kt`; buttons → pill via global shape override; sidebar, home hero, cards radius pass; player buttons/slider; mini player bar; sheets.
- Verify: screenshot pass on all screens (light/dark, pure black), contrast spot-checks.

**Phase 2 — Liquid Glass opt-in:** `GlassSurface` + blur modifier, fallback for API < 31 / low-RAM; player detail panel, auth card, sidebar; Settings "Look" picker + preview.
- Verify: toggle on/off live, scroll performance (frame rate), battery (blur off when screen idle/not playing).

**Phase 3 — Consistency sweep:** Auth colors unification, Wrapped token inherit, SharedPlaylist/Artist headers, menu alignment; design-token checklist review.
- Verify: full `assembleFossDebug` + install, manual QA script (all 12 screen groups), CHANGES.md entry.

**Not doing (scope guard):** no new navigation, no DB changes, no typography family change, no per-screen bespoke colors, no version bump.

## 7. Risks & mitigations

- **Blur perf/battery (Liquid Glass):** limit blur layers (max 2), disable during scrolling on low-RAM devices, fallback solid scrims; measure with profiler.
- **Contrast:** tonal surface refresh audited against WCAG AA for cream text; keep `pureBlack` option functional.
- **Regressions:** every phase lands behind the `ThemeVariant` default so current users see Earthy until Material U flips (or flip with the feature version); Wrapped untouched.
- **Settings scope:** only additive settings rows; no migration of existing prefs.

## 8. Deliverables checklist (when implemented)

- [ ] `ThemeVariant` + "Look" setting (Earthy U default, Liquid Glass experimental)
- [ ] Material U palettes + shapes in `Theme.kt`
- [ ] `GlassSurface`/blur modifier with fallback
- [ ] Player + mini player glass/tint pass
- [ ] Sidebar/Home/Search/Library/Playlists/Artist/SharedPass headers + cards
- [ ] Pill buttons everywhere (spec §4)
- [ ] Auth unified with seed color
- [ ] Build + install + manual QA + CHANGES.md entry