-- OTP send-tracking for the resend policy (1-minute cooldown, 10-minute
-- validity, "still valid" error once per send with an escape-hatch resend).
-- Applied 2026-08-06.
alter table public.otp_codes
  add column if not exists notified boolean default false;

-- otp_hash is no longer stored: codes live entirely in Supabase Auth.
alter table public.otp_codes
  alter column otp_hash drop not null;
