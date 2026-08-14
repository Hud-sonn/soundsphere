/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.data

import com.soundsphere.music.BuildConfig
import timber.log.Timber

/**
 * Single source of truth for the backend base URL with automatic failover.
 *
 * Requests start on [BuildConfig.API_BASE_URL] — the production domain
 * `api.soundsphere.name.ng` — and fall back to
 * [BuildConfig.API_FALLBACK_BASE_URL] (the onrender.com host) as soon as a
 * network-level failure proves the primary unreachable. The fallback is
 * sticky until a successful call (see [markSuccess]), which also keeps the
 * app from hammering a dead primary on every request.
 *
 * Callers: SyncService, AuthService and RenderKeepAliveWorker. Debug builds
 * may point the primary at a local dev server via `AUTH_DEV_BASE_URL`; the
 * fallback stays the production onrender host so a dev server that dies
 * never bricks the app.
 */
object BackendEndpoint {

    @Volatile
    private var current: String = BuildConfig.API_BASE_URL

    /** The base URL to use for the next request. */
    fun current(): String = current

    /** Called when a request fails at the network layer (IOException). */
    fun markFailure() {
        if (current != BuildConfig.API_FALLBACK_BASE_URL) {
            Timber.w("BackendEndpoint: primary unreachable, switching to ${BuildConfig.API_FALLBACK_BASE_URL}")
            current = BuildConfig.API_FALLBACK_BASE_URL
        }
    }

    /** Called when a request succeeds; switches back to the primary. */
    fun markSuccess() {
        if (current != BuildConfig.API_BASE_URL) {
            Timber.i("BackendEndpoint: primary reachable again, switching back to ${BuildConfig.API_BASE_URL}")
            current = BuildConfig.API_BASE_URL
        }
    }
}