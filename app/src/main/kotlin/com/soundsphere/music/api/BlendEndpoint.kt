package com.soundsphere.music.api

import com.soundsphere.music.BuildConfig

/**
 * Second backend endpoint for Blend (collaborative playlists) when double-project
 * isolation is enabled. Mirrors `BackendEndpoint` but points at `BLEND_BASE_URL`.
 *
 * When the second Supabase project is not yet active, this will 404 — the app
 * falls back to single-project mode (all Blend calls go through `SyncService`
 * on the primary backend, which is how v1 ships). Once `soundsphere-blend`
 * is live with the same `JWT_SECRET`, the same `Authorization: Bearer <JWT>`
 * from `AuthRepository` will verify there with zero DB lookup.
 */
object BlendEndpoint {
    @Volatile var current: String = BuildConfig.BLEND_BASE_URL
    private val primary = BuildConfig.BLEND_BASE_URL
    // No fallback for Blend yet — could add a second BLEND_FALLBACK if needed.

    fun markFailure() {
        // No-op for now — single URL. If a fallback is added, switch here.
    }

    fun markSuccess() {
        current = primary
    }

    fun isConfigured(): Boolean = primary.isNotBlank() && !primary.contains("YOUR_")
}
