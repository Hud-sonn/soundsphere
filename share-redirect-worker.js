// share-redirect-worker.js
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

async function proxy(targetUrl, originalRequest) {
  const backendResponse = await fetch(targetUrl, {
    method: originalRequest.method,
    headers: originalRequest.headers,
    body: originalRequest.method === 'GET' || originalRequest.method === 'HEAD'
      ? undefined
      : originalRequest.body,
    redirect: 'manual'
  });

  if (backendResponse.status >= 300 && backendResponse.status < 400) {
    const location = backendResponse.headers.get('Location');
    if (location) {
      const rewritten = location.replace(BACKEND, 'https://share.soundsphere.name.ng');
      return new Response(null, {
        status: backendResponse.status,
        headers: { Location: rewritten }
      });
    }
  }

  const contentType = backendResponse.headers.get('content-type') || '';

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

  const responseHeaders = new Headers(backendResponse.headers);
  responseHeaders.delete('x-powered-by');
  responseHeaders.delete('server');

  return new Response(backendResponse.body, {
    status: backendResponse.status,
    headers: responseHeaders
  });
}
