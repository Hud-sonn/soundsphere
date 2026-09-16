# Cloudflare Worker — Share URL Redirect

Date: 2026-09-14
Status: Proposal (NOT implemented)
Why: Hide `api.soundsphere.name.ng` from user-facing share links by routing through `share.soundsphere.name.ng` via Cloudflare Worker.

## How it works

```
User shares playlist → https://share.soundsphere.name.ng/p/abc123
                           ↓
                    Cloudflare Worker
                           ↓
                    302 → soundsphere://p/abc123 (opens app)
```

The backend URL `api.soundsphere.name.ng` never appears in any share link. The Worker just does redirects — zero logic, zero cost.

## Worker code

```javascript
// File: share-redirect-worker.js
// Deploy to: share.soundsphere.name.ng
// Cloudflare Workers free tier: 100k requests/day
//
// PROXIES share requests to the backend. Browser URL stays on
// share.soundsphere.name.ng — backend URL never exposed.
//
// User opens: https://share.soundsphere.name.ng/p/abc123
// Worker fetches: https://api.soundsphere.name.ng/p/abc123
// Browser sees:  https://share.soundsphere.name.ng/p/abc123

const BACKEND = 'https://api.soundsphere.name.ng';

export default {
  async fetch(request) {
    const url = new URL(request.url);
    const path = url.pathname;

    // Playlist share: /p/{token}
    const playlistMatch = path.match(/^\/p\/([a-zA-Z0-9_-]+)$/);
    if (playlistMatch) {
      const token = playlistMatch[1];
      return proxy(`${BACKEND}/p/${token}`, request);
    }

    // Song share: /s/{id}
    const songMatch = path.match(/^\/s\/([a-zA-Z0-9_-]+)$/);
    if (songMatch) {
      const id = songMatch[1];
      return proxy(`${BACKEND}/s/${id}`, request);
    }

    // Blend invite: /b/{token}
    const blendMatch = path.match(/^\/b\/([a-zA-Z0-9_-]+)$/);
    if (blendMatch) {
      const token = blendMatch[1];
      return proxy(`${BACKEND}/b/${token}`, request);
    }

    // Fallback — redirect to main site
    return Response.redirect('https://soundsphere.name.ng', 302);
  }
};

// Proxy: fetch from backend, rewrite og:url and Location headers.
// Browser URL stays on share.soundsphere.name.ng.
async function proxy(targetUrl, originalRequest) {
  const backendResponse = await fetch(targetUrl, {
    method: originalRequest.method,
    headers: originalRequest.headers,
    body: originalRequest.method === 'GET' || originalRequest.method === 'HEAD'
      ? undefined
      : originalRequest.body,
    redirect: 'manual' // don't follow redirects — rewrite them
  });

  // If backend returns a redirect, rewrite Location to share domain
  if (backendResponse.status >= 300 && backendResponse.status < 400) {
    const location = backendResponse.headers.get('Location');
    if (location) {
      const rewritten = location
        .replace(BACKEND, 'https://share.soundsphere.name.ng');
      return new Response(null, {
        status: backendResponse.status,
        headers: { Location: rewritten }
      });
    }
  }

  const contentType = backendResponse.headers.get('content-type') || '';

  // HTML responses: rewrite og:url and any hardcoded backend URLs
  if (contentType.includes('text/html')) {
    let html = await backendResponse.text();
    html = html.replaceAll('api.soundsphere.name.ng', 'share.soundsphere.name.ng');

    const responseHeaders = new Headers(backendResponse.headers);
    responseHeaders.delete('x-powered-by');
    responseHeaders.delete('server');
    responseHeaders.set('content-type', 'text/html; charset=utf-8');

    return new Response(html, {
      status: backendResponse.status,
      headers: responseHeaders
    });
  }

  // Non-HTML: pass through (strip backend headers)
  const responseHeaders = new Headers(backendResponse.headers);
  responseHeaders.delete('x-powered-by');
  responseHeaders.delete('server');

  return new Response(backendResponse.body, {
    status: backendResponse.status,
    headers: responseHeaders
  });
}
```

## Setup steps

### 1. Add domain to Cloudflare
1. Go to https://dash.cloudflare.com → Sign up (free)
2. Click "Add a site" → Enter `soundsphere.name.ng`
3. Select Free plan
4. Cloudflare scans existing DNS records — let it finish
5. Cloudflare gives you 2 nameservers (e.g. `anna.ns.cloudflare.com`)

### 2. Change nameservers at WhoGoHost
1. Log in to WhoGoHost dashboard
2. Go to Domain Management → `soundsphere.name.ng` → Nameservers
3. Replace WhoGoHost's nameservers with the 2 Cloudflare nameservers
4. Save — wait for propagation (5 min to 24 hours, usually < 1 hour)
5. Do NOT touch any A/CNAME records — Cloudflare imported them, they keep working

### 3. Verify in Cloudflare
1. Back in Cloudflare dashboard → `soundsphere.name.ng` → Overview
2. Wait for status to change from "Pending" to "Active" (green cloud)
3. Go to DNS → Records — confirm all existing records are there

### 4. Create the Worker
1. Cloudflare dashboard → Workers & Pages → Create Worker
2. Name it `share-redirect`
3. Paste the worker code above
4. Click "Deploy"

### 5. Add route for the subdomain
1. Cloudflare dashboard → `soundsphere.name.ng` → Workers & Pages
2. Click your `share-redirect` worker → Triggers → Add Route
3. Add these routes:
   - `share.soundsphere.name.ng/p/*`
   - `share.soundsphere.name.ng/s/*`
   - `share.soundsphere.name.ng/b/*`
4. Save

### 6. Test
1. Open `https://share.soundsphere.name.ng/p/someToken` in browser
2. Should redirect to `soundsphere://p/someToken`
3. On phone with Soundsphere installed → opens the app
4. On desktop → shows "can't open" (expected — deep links are mobile-only)

### 7. Update app (when ready)
Change share strings in `soundsphere_strings.xml`:
- `https://api.soundsphere.name.ng/p/%3$s` → `https://share.soundsphere.name.ng/p/%3$s`
- `https://api.soundsphere.name.ng/s/%3$s` → `https://share.soundsphere.name.ng/s/%3$s`

## What breaks during nameserver propagation
- Nothing — Cloudflare imports all existing DNS records
- `api.soundsphere.name.ng` keeps pointing to Render
- `soundsphere.name.ng` keeps working
- Email (if any) keeps working

## What you get
- `api.soundsphere.name.ng` stays internal — never in share links
- Cloudflare DDoS protection on all traffic
- Worker is free (100k requests/day)
- SSL handled by Cloudflare automatically
- No changes to Render, WhoGoHost, or existing backend
