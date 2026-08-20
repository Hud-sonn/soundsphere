-- Per-user daily AI playlist generation budget.
-- Account-level (keyed by user_id, not device/IP) so clearing the app cache
-- or switching devices cannot reset the limit.
-- Referenced by /ai/generate-playlist (routers/ai.py).
-- Apply in the Supabase SQL editor; backend writes use the service role.
create table if not exists public.ai_generation_usage (
  user_id uuid not null references public.users (id) on delete cascade,
  usage_date date not null default current_date,
  generation_count integer not null default 0,
  updated_at timestamptz not null default now(),
  primary key (user_id, usage_date)
);