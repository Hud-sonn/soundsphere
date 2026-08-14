# Design Options — Soundsphere

Two visual direction proposals for the app, both Material 3 compatible.
Pick one (or a blend) and we'll implement it as a theme variant in
`Theme.kt` / the theme picker.

---

## Option A — "Material U" (Material You / Dynamic Color)

Material You gone maximal: the whole app becomes a canvas for the user's
wallpaper and brand palette.

**Colors**
- Dynamic color stays the default, but the **seed color is user-takable**:
  pick from wallpaper, cover art of the currently playing song, or any
  custom hex.
- Default color scheme becomes the expressive `Fidelity`/`Vibrant` dynamic
  variants (richer tonal palettes than the current `TonalSpot`) when
  dynamic color is on.
- Hard-coded `surfaceContainer`/`surfaceVariant` combos are replaced by
  tonal-role components (the M3 way): cards use `surfaceContainerLow`,
  sheets `surfaceContainerHigh`, so every screen tints consistently.

**Shapes**
- Shape scale pushed rounder: `small = 8dp`, `medium = 16dp`,
  `large = 28dp` — buttons, cards, chips and dialogs all grow softer.
- "Expressive" hover/selected states: chips become container-shaped when
  selected, toggle colors animate through the tonal palette.

**Typography**
- Keep Space Grotesk for headings, but add a mono-ish accent for
  numbers/counters (track counts, durations) — a tiny, cheap way to feel
  designed.

**Motions**
- `spring`-based (bouncy) transitions for bottom sheets and the player
  expansion; standard `tween` everywhere else.
- Animated icon morphs on selected nav items (outline → filled) instead of
  instant swaps.

**Why it fits Soundsphere**
- Music apps live on album art; a palette that reacts to the playing song
  makes the app feel alive. Low effort, high impact — mostly token swaps.

---

## Option B — "Liquid Glass"

A frosted, translucent layer over the artwork: glassmorphism done
Android-style, playing nicely with dynamic color.

**Core recipe**
- Backgrounds: the Home / player / sheets get a blurred, dimmed
  **album-art or wallpaper layer** behind translucent surfaces
  (alpha 55–75%).
- Surfaces: `Color(0xCC…)` glass panels with a **1dp white/black
  specular border** (top edge brighter — light catches the glass).
- Contrast handled by the M3 tonal system on top: text stays
  `onSurface` with readable contrast because the blur base is dimmed.

**Implementation notes (Compose)**
- Android 12+: true **backdrop blur** via `RenderEffect` on a
  `GraphicsLayer` behind `Surface`s, or the cheaper approach —
  `Modifier.blur()` on a static artwork layer composited behind
  translucent panels (works on all API levels, slightly heavier).
- Player screen: the big square artwork sits full-bleed behind the
  controls; the control area is a glass `Surface` with the artwork
  blurred in a `Canvas`/`Image` behind it, `alpha(0.85f)`.
- Sheets & dialogs: `sheetContainerColor = surface.withAlpha(0.8f)`
  + 1dp `border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))`,
  with a matching scrim (the default scrim is fine).
- No blur on API < 26: fall back to a flat translucent surface with a
  noise/grain overlay so the aesthetic still reads as glass.

**Why it fits Soundsphere**
- This is the current "premium music player" look (Apple Music, YouTube
  Music player views). Strong visual identity, but more perf-sensitive —
  blur is GPU work; needs the RAM/GPU-light testing on the TECNO device.

---

## Recommendation

- **Ship A first** (token-level, near-zero risk, works everywhere).
- **Add B as the "Liquid Glass" theme option** behind dynamic color
  (opt-in, default off), with the blur fallback for older devices.

Both live in the existing `SelectedThemeColorKey`/theme-picker system as
new variants, so nothing existing breaks.