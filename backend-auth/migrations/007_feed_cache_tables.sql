-- Feed cache tables for new releases + upcoming concerts.
-- Written by the scraper cron job (service_role), read by /feed/* endpoints.
-- RLS: NO authenticated INSERT/UPDATE policy — only service_role writes.
-- Users read via backend API which joins cache with followed_artists.

-- New releases from iTunes + Deezer, keyed by (artist_name, source, source_id).
create table if not exists public.artist_releases_cache (
  artist_name text not null,
  source text not null,          -- 'itunes' / 'deezer'
  source_id text not null,       -- album/single ID from the source
  title text not null default '',
  release_date date,
  album_type text default '',    -- 'album' / 'single' / 'ep'
  artwork text default '',
  url text default '',
  track_count integer default 0,
  fetched_at timestamptz not null default now(),
  primary key (artist_name, source, source_id)
);

-- Upcoming concerts from Bandsintown + tickethub + padeya.
create table if not exists public.artist_events_cache (
  source text not null,          -- 'bandsintown' / 'tickethub' / 'padeya'
  source_id text not null,       -- event ID from the source
  title text not null default '',
  artist_name text,              -- matched artist (nullable for multi-artist events)
  url text default '',
  start_time timestamptz,
  end_time timestamptz,
  venue text default '',
  city text default '',
  country text default '',
  image text default '',
  ticket_url text default '',
  sold_out boolean default false,
  description text default '',
  fetched_at timestamptz not null default now(),
  primary key (source, source_id)
);

-- Index for the most common query: releases for a set of artists, sorted by date.
create index if not exists idx_releases_artist_date
  on public.artist_releases_cache (artist_name, release_date desc);

-- Index for events: upcoming events for a set of artists, sorted by start time.
create index if not exists idx_events_artist_start
  on public.artist_events_cache (artist_name, start_time asc);

-- Index for cleanup: delete old cache entries.
create index if not exists idx_releases_fetched
  on public.artist_releases_cache (fetched_at);

create index if not exists idx_events_fetched
  on public.artist_events_cache (fetched_at);

-- RLS: enable but NO policies for authenticated role.
-- Only service_role (backend) can write/read.
alter table public.artist_releases_cache enable row level security;
alter table public.artist_events_cache enable row level security;
