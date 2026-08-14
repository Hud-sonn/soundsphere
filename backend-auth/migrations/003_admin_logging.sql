-- Admin logging: API error log + crash reports.
-- api_error_logs: written by the error-logging middleware in main.py for
-- every 4xx/5xx response (including slowapi 429s), so the admin dashboard
-- can show who is hitting which endpoint and failing.
-- crash_reports: uploaded by the Android app (POST /admin/crashes/ingest).
-- Apply in the Supabase SQL editor; backend writes use the service role.
create table if not exists public.api_error_logs (
  id uuid primary key default gen_random_uuid(),
  method text not null default '',
  path text not null default '',
  status_code int not null,
  client_ip text not null default '',
  user_id uuid references public.users (id) on delete set null,
  detail text not null default '',
  created_at timestamptz not null default now()
);
create index if not exists api_error_logs_created_at_idx
  on public.api_error_logs (created_at desc);
create index if not exists api_error_logs_status_idx
  on public.api_error_logs (status_code);
create index if not exists api_error_logs_ip_idx
  on public.api_error_logs (client_ip);

create table if not exists public.crash_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.users (id) on delete set null,
  app_version text not null default '',
  app_version_code int not null default 0,
  flavor text not null default '',
  os text not null default '',
  device_model text not null default '',
  manufacturer text not null default '',
  exception text not null default '',
  message text not null default '',
  stack_trace text not null,
  thread_name text not null default '',
  fatal boolean not null default true,
  reported_at timestamptz not null default now(),
  handled boolean not null default false
);
create index if not exists crash_reports_reported_at_idx
  on public.crash_reports (reported_at desc);
create index if not exists crash_reports_app_version_idx
  on public.crash_reports (app_version);
