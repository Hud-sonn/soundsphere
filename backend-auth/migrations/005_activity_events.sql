-- Activity feed for the admin dashboard: every interesting backend event
-- (logins, signups, app usage, share-link views, errors, crashes) lands here
-- so the dashboard can show what is happening live.
-- Apply in the Supabase SQL editor; backend writes use the service role.
create table if not exists public.activity_events (
  id bigserial primary key,
  user_id text,
  event_type text not null,
  detail text not null default '',
  created_at timestamptz not null default now()
);

create index if not exists activity_events_created_idx
  on public.activity_events (created_at desc);