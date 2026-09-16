import os
import asyncio
import logging
from contextlib import asynccontextmanager
from dotenv import load_dotenv

import httpx

load_dotenv()

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware
from services.limiter import limiter
from services.activity import log_activity
from routers.auth import router as auth_router
from routers.user import router as user_router
from routers.ai import router as ai_router
from routers.admin import router as admin_router
from routers.share import router as share_router
from routers.web import router as web_router
from routers.feed import router as feed_router
from db.supabase import get_supabase

REQUIRED = [
    "SUPABASE_URL",
    "SUPABASE_SERVICE_KEY",
    "JWT_SECRET",
    "GMAIL_USER",
    "GMAIL_APP_PASSWORD",
]
for var in REQUIRED:
    if not os.getenv(var):
        raise RuntimeError(f"Missing required env var: {var}")

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("soundsphere-auth")


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.limiter = limiter
    # Keep second backend + its DB warm so Blend doesn't cold-start.
    # Main backend pings the Blend backend's /health every 5 min; that keeps
    # the free Render instance awake and, via the second backend's own DB
    # pool, keeps its Supabase DB connection warm. No user data is touched.
    blend_url = os.getenv("BLEND_BASE_URL", "https://soundsphere-blend.onrender.com").rstrip("/")
    keepalive_task = None
    feed_refresh_task = None
    if blend_url:
        async def _keepalive_loop():
            # Wait for app to be fully up before first ping
            await asyncio.sleep(10)
            async with httpx.AsyncClient(timeout=10) as client:
                while True:
                    try:
                        # Ping health on second backend — keeps its Render free tier awake
                        await client.get(f"{blend_url}/health")
                        # Also hit a lightweight DB-touching endpoint if available;
                        # /health on the second host with the same code will also
                        # warm its Supabase pool on the next real request.
                    except Exception as e:
                        logger.debug(f"Blend keepalive ping failed: {e}")
                    await asyncio.sleep(300)  # 5 min

        keepalive_task = asyncio.create_task(_keepalive_loop())

    # Feed cache refresh: runs every 12 hours.
    # Populates artist_releases_cache + artist_events_cache from
    # followed_artists → iTunes/Deezer/Bandsintown/tickethub.
    async def _feed_refresh_loop():
        # Wait 60s after startup before first refresh (let app settle)
        await asyncio.sleep(60)
        while True:
            try:
                from services.feed_refresh import refresh_feed
                loop = asyncio.get_running_loop()
                summary = await loop.run_in_executor(None, refresh_feed)
                logger.info("Feed refresh (background): %s", summary)
            except Exception as e:
                logger.exception("Feed refresh failed: %s", e)
            # Sleep 12 hours
            await asyncio.sleep(12 * 60 * 60)

    feed_refresh_task = asyncio.create_task(_feed_refresh_loop())

    try:
        yield
    finally:
        if keepalive_task:
            keepalive_task.cancel()
            try:
                await keepalive_task
            except asyncio.CancelledError:
                pass
        if feed_refresh_task:
            feed_refresh_task.cancel()
            try:
                await feed_refresh_task
            except asyncio.CancelledError:
                pass


app = FastAPI(title="Soundsphere Auth API", version="1.0.0", lifespan=lifespan)
app.state.limiter = limiter
app.add_middleware(SlowAPIMiddleware)
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)


# Security headers middleware
@app.middleware("http")
async def add_security_headers(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["X-XSS-Protection"] = "1; mode=block"
    response.headers["Strict-Transport-Security"] = (
        "max-age=31536000; includeSubDomains"
    )
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    return response


# Trusted hosts middleware
# Trusted hosts middleware. PRODUCTION list: only the app's domain and the
# onrender host. Deliberately NO localhost/127.0.0.1 here — allowing them
# would let anyone spoof the Host header (DNS-rebinding protection gone).
# When testing the backend locally you MUST add localhost via the env var,
# e.g. ALLOWED_HOSTS="localhost,api.soundsphere.name.ng,soundsphere-auth.onrender.com"
allowed_hosts = os.getenv(
    "ALLOWED_HOSTS", "api.soundsphere.name.ng,soundsphere-auth.onrender.com"
)
app.add_middleware(
    TrustedHostMiddleware,
    allowed_hosts=allowed_hosts.split(","),
)

origins = os.getenv(
    "ALLOWED_ORIGINS", "http://localhost:8081,http://localhost:3000"
).split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)

app.include_router(auth_router)
app.include_router(user_router)
app.include_router(ai_router)
app.include_router(admin_router)
app.include_router(share_router)
app.include_router(web_router)
app.include_router(feed_router)


def _record_error_log(method: str, path: str, status_code: int, client_ip: str, detail: str = ""):
    """Persist an error-log row for the admin dashboard. Runs in a worker
    thread so slow Supabase writes never block request handling."""
    try:
        db = get_supabase()
        db.table("api_error_logs").insert(
            {
                "method": method,
                "path": path,
                "status_code": status_code,
                "client_ip": client_ip,
                "detail": detail,
            }
        ).execute()
        log_activity(None, "error", f"{status_code} {method} {path}")
    except Exception:
        logger.exception("Failed to write api_error_log row")


@app.middleware("http")
async def log_http_errors(request: Request, call_next):
    """Record every 4xx/5xx response (including slowapi 429s) into
    api_error_logs so the admin dashboard can surface errors."""
    response = await call_next(request)
    if response.status_code >= 400:
        detail = ""
        if response.status_code == 429:
            detail = "rate limit exceeded"
        client_ip = request.client.host if request.client else ""
        asyncio.get_running_loop().run_in_executor(
            None,
            _record_error_log,
            request.method,
            request.url.path,
            response.status_code,
            client_ip,
            detail,
        )
    return response


@app.get("/health")
@limiter.limit("100/minute")
async def health(request: Request):
    return {"status": "ok", "version": os.getenv("APP_VERSION", "1.0.0")}


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    # Full traceback (not just the type) so Render logs are actually debuggable.
    logger.exception(f"Unhandled exception on {request.method} {request.url.path}")

    return JSONResponse(status_code=500, content={"detail": "Internal server error"})


@app.exception_handler(httpx.TransportError)
async def supabase_transport_error_handler(request: Request, exc: httpx.TransportError):
    """Transient network failures between Render and Supabase (httpx ReadError
    etc). Surface a retryable 503 instead of an unhandled 500 + traceback."""
    logger.warning(
        "Upstream transport error on %s %s: %s",
        request.method,
        request.url.path,
        exc,
    )
    return JSONResponse(status_code=503, content={"detail": "Upstream storage unavailable, retry"})


# Admin web UI (static HTML/JS/CSS). Mounted last so API routes above win.
_admin_web_dir = os.path.join(os.path.dirname(__file__), "admin_web")
if os.path.isdir(_admin_web_dir):
    app.mount("/admin", StaticFiles(directory=_admin_web_dir, html=True), name="admin_web")

# Docs — HTML renderings of the repo's MDs, viewable only from the backend domain.
# Each doc has a Date/Status/Why header per AGENTS.md, and they are linked via /docs/index.html.
_docs_dir = os.path.join(os.path.dirname(__file__), "docs")
if os.path.isdir(_docs_dir):
    app.mount("/docs", StaticFiles(directory=_docs_dir, html=True), name="docs")
