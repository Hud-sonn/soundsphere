package com.soundsphere.music.data

import android.content.Context
import com.soundsphere.music.api.BlendService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the second backend (Blend isolation) — see `INVESTIGATION_BLEND.md`
 * § "How a Second Backend Would Work". Same JWT as primary, different `SUPABASE_URL`.
 *
 * v1: single-project mode — all Blend calls go through `SyncRepository`/`SyncService`
 * on `ysfktparruosuegzdnwt` (solus rift). This class is scaffolding for the
 * double-project cutover: when `yuukseizasygckaeonrh` (keysheild) is `ACTIVE`
 * and `soundsphere-blend.onrender.com` is live with the same `JWT_SECRET`,
 * move `joinBlend`/`getCollaborators`/`addTrack` here and point `BlendEndpoint`
 * at it. No login change — `AuthRepository.getToken()` is reused.
 */
@Singleton
class BlendRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
) {
    val isDoubleProjectEnabled: Boolean get() = com.soundsphere.music.api.BlendEndpoint.isConfigured()
}
