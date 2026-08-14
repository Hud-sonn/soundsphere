-- Public playlist sharing: unguessable per-playlist share token.
-- A NULL token means "not shared". Indexed for O(1) public lookups.
-- Apply in the Supabase SQL editor; backend writes use the service role.
alter table public.playlists
  add column if not exists share_token text;

create unique index if not exists playlists_share_token_idx
  on public.playlists (share_token) where share_token is not null;