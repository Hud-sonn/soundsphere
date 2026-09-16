"""International events via Bandsintown REST API.

Uses rest.bandsintown.com with public app_id 'js_api_client' (keyless).
Returns rich JSON: venue, lineup, ticket links, coordinates.

The schema.org Event JSON-LD extractor (extract_schema_events) is kept
for padeya.com compatibility — padeya detail pages carry the same markup.
"""
