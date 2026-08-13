from pydantic import BaseModel, EmailStr, Field
from typing import Optional


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8)
    username: str = Field(min_length=3)


class VerifyOtpRequest(BaseModel):
    email: EmailStr
    otp: str = Field(min_length=6, max_length=8)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class ForgotPasswordRequest(BaseModel):
    email: EmailStr


class ResetPasswordRequest(BaseModel):
    email: EmailStr
    otp: str = Field(min_length=6, max_length=8)
    new_password: str = Field(min_length=8)


class ResendOtpRequest(BaseModel):
    email: EmailStr


class UserResponse(BaseModel):
    id: str
    email: str
    username: str
    avatar_url: Optional[str] = None
    auth_provider: str
    is_verified: bool
    role: str = "user"
    created_at: str


class TokenResponse(BaseModel):
    token: str
    user: UserResponse


class TrackPayload(BaseModel):
    """Track metadata stored in the `tracks` table. The `id` must already be
    globally unique (e.g. a YouTube video id); relations (liked/history/
    playlist_tracks) reference it via foreign keys, so a track row must be
    upserted before any relation insert succeeds."""

    id: str = Field(min_length=1, max_length=128)
    title: str = Field(min_length=1, max_length=512)
    artist: str = Field(min_length=0, max_length=512)
    album: Optional[str] = Field(default=None, max_length=512)
    duration: int = 0
    artwork_url: Optional[str] = Field(default=None, max_length=2048)
    source: str = Field(default="youtube", pattern="^(audius|youtube)$")
    genre: Optional[str] = Field(default=None, max_length=128)
    year: Optional[int] = None


class LikeTrackRequest(TrackPayload):
    id: str | None = None


class PlaylistCreateRequest(BaseModel):
    name: str = Field(min_length=1, max_length=256)
    cover_url: Optional[str] = Field(default=None, max_length=2048)


class PlaylistUpdateRequest(BaseModel):
    name: Optional[str] = Field(default=None, min_length=1, max_length=256)
    cover_url: Optional[str] = Field(default=None, max_length=2048)


class AddPlaylistTrackRequest(BaseModel):
    track: TrackPayload
    position: Optional[int] = None


class HistoryAddRequest(BaseModel):
    track: TrackPayload
    played_at: Optional[str] = None


class FollowAddRequest(BaseModel):
    artist_name: Optional[str] = Field(default=None, max_length=512)


class ProfileUpdateRequest(BaseModel):
    username: Optional[str] = Field(default=None, min_length=3, max_length=64)
    avatar_url: Optional[str] = Field(default=None, max_length=2048)


class SettingsUpdateRequest(BaseModel):
    settings: dict = Field(default_factory=dict)


class AiPlaylistRequest(BaseModel):
    """Prompt for the server-side AI playlist generator.

    The Groq API key lives on the server; the app only sends the prompt and
    receives resolved track suggestions (the server resolves them against
    YouTube Music search results).
    """

    prompt: str = Field(min_length=3, max_length=1024)
    count: int = Field(default=30, ge=30, le=50)
