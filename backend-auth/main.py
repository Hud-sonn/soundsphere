import os
import asyncio
import logging
from contextlib import asynccontextmanager
from dotenv import load_dotenv

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
from routers.auth import router as auth_router
from routers.user import router as user_router
from routers.ai import router as ai_router
from routers.admin import router as admin_router
from routers.share import router as share_router
from routers.web import router as web_router
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
    yield


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
    logger.error(f"Unhandled exception: {type(exc).__name__}")

    return JSONResponse(status_code=500, content={"detail": "Internal server error"})


# Admin web UI (static HTML/JS/CSS). Mounted last so API routes above win.
_admin_web_dir = os.path.join(os.path.dirname(__file__), "admin_web")
if os.path.isdir(_admin_web_dir):
    app.mount("/admin", StaticFiles(directory=_admin_web_dir, html=True), name="admin_web")
