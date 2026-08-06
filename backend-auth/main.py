import os
import logging
from contextlib import asynccontextmanager
from dotenv import load_dotenv

load_dotenv()

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import JSONResponse
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware
from services.limiter import limiter
from routers.auth import router as auth_router
from routers.user import router as user_router

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
allowed_hosts = os.getenv(
    "ALLOWED_HOSTS", "localhost,127.0.0.1,*.onrender.com"
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


@app.get("/health")
@limiter.limit("100/minute")
async def health(request: Request):
    return {"status": "ok", "version": os.getenv("APP_VERSION", "1.0.0")}


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception: {type(exc).__name__}")

    return JSONResponse(status_code=500, content={"detail": "Internal server error"})
