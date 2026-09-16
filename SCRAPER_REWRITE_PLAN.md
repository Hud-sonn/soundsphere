# Date: 2026-09-16
# Status: Active
# Why: Rewrite Bandsintown scraper from blocked website scraping to working REST API, document full architecture

# Scraper Rewrite + Feed Architecture Plan

## Scope: Releases + Concerts ONLY

This plan covers two features:
1. **New Release Radar** — new albums/singles from artists the user follows
2. **Concert/Event Feed** — upcoming concerts from artists the user follows

UI work (home screen sections, event screen, new releases screen) is NOT part of this plan.

## Data flow

```
User follows artist → followed_artists table
                            ↓
Cron (every 12h) → reads unique artist_name FROM followed_artists
                            ↓
              ┌─────────────┼─────────────┐
              ↓             ↓             ↓
         iTunes+Deezer   Bandsintown   tickethub/padeya
         (releases)      (events)      (Nigerian events)
              ↓             ↓             ↓
        artist_releases_cache   artist_events_cache
              ↓             ↓             ↓
         GET /feed/releases  GET /feed/events
         (join cache + user's followed_artists)
```

## Per-user behavior

- `followed_artists` table IS the per-user artist list
- No separate search history needed
- Cron fetches globally (all unique artist names), caches results
- API serves personalized views by joining cache + follows
- When user follows/unfollows → cron picks up on next run

## Cache tables (Supabase, service_role-only writes)

### artist_releases_cache
- `artist_name` text (PK part)
- `source` text (itunes/deezer) (PK part)
- `source_id` text (PK part) — album/single ID
- `title` text
- `release_date` date
- `album_type` text (album/single/ep)
- `artwork` text (600px URL)
- `url` text
- `track_count` int
- `fetched_at` timestamptz

### artist_events_cache
- `source` text (tickethub/padeya/bandsintown) (PK part)
- `source_id` text (PK part)
- `title` text
- `artist_name` text (nullable — events can have multiple artists)
- `url` text
- `start_time` timestamptz
- `end_time` timestamptz (nullable)
- `venue` text
- `city` text
- `country` text
- `image` text
- `ticket_url` text
- `sold_out` bool
- `description` text
- `fetched_at` timestamptz

## Expiration

- **Events:** API filters `WHERE start_time > now()` — events auto-disappear after they happen
- **Releases:** API filters `WHERE release_date > (now() - interval '7 days')` — keeps last 7 days of new releases

## Backend capacity (verified 2026-09-16)

- soundsphere-auth: 90 MB / 512 MB, 0.2% CPU → can handle scrapers
- soundsphere-blend: 79 MB / 512 MB → available if needed
- Scraper cron adds ~10-20 MB for ~10s every 12h → negligible

## What's left after this plan

- Home screen sections (user designs) — reference mock: `HOME_SCREEN_MOCK.html`
- Event screen (user designs)
- New releases screen (user designs)

## Home screen mock sections (from HOME_SCREEN_MOCK.html)

| Section | Type | Data source | Notes |
|---|---|---|---|
| Live Concert Alert Ribbon | Single event highlight | `artist_events_cache` (top event by start_time) | Shows venue, date, ticket %, Tickets button |
| New Release Radar | Hero card | `artist_releases_cache` (latest release) | Album art, artist, track count, duration, waveform preview |
| Acoustic World Tour | Horizontal scroll cards | `artist_events_cache` (upcoming events) | Venue image, city, date, price, ticket status bar |
| Heavy Rotation | Track list | Existing history/liked data | NOT part of this feature |
| Mini Player | Existing component | No changes | |
| Bottom Nav | Home, Events, Library, Blend | New "Events" tab | |
