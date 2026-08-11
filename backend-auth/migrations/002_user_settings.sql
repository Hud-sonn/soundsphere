-- Per-user app settings (JSONB), including the AI playlist consent flag.
-- Referenced by GET/PUT /user/settings (routers/user.py) and the
-- /ai/generate-playlist consent gate (routers/ai.py).
-- Apply in the Supabase SQL editor; backend writes use the service role.
create table if not exists public.user_settings (
  user_id uuid primary key references public.users (id) on delete cascade,
  settings jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
