"""Soundsphere concert/release scrapers.

Each source lives in its own folder so rate limits, markup quirks and
bot-wall workarounds stay isolated:

- bandsintown/ — international events via REST API (rest.bandsintown.com, public app_id)
- nigerian/    — tickethub.ng + padeya.com (Next.js flight-data parsers)
- releases/    — artist new releases (iTunes + Deezer, both keyless)

Only stdlib + httpx (already a backend dependency). No browser automation.
Run backend-auth/scrapers/test_scrapers.py to verify each source.
"""
