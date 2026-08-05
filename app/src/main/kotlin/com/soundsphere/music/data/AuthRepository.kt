/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the auth JWT securely using Android Keystore-backed
 * EncryptedSharedPreferences. Never store auth tokens in plain DataStore.
 */
@Singleton
class AuthRepository @Inject constructor(
    context: Context,
) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)
    private val _isLoggedIn = MutableStateFlow(prefs.contains(KEY_TOKEN))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _isLoggedIn.value = true
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _isLoggedIn.value = false
    }

    fun isLoggedIn(): Boolean = _isLoggedIn.value

    private companion object {
        const val KEY_TOKEN = "auth_token"
        const val PREFS_NAME = "soundsphere_auth"

        fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                // Android Keystore initialization can fail on some OEM devices
                // (e.g. certain Tecno family devices). A plain prefs fallback keeps
                // the app usable — degraded security on that one device beats a
                // permanently bricked cold start. Log it so the fallback is visible
                // if it ever triggers for a real user.
                Timber.w(e, "EncryptedSharedPreferences unavailable; falling back to plain SharedPreferences")
                context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
            }
        }
    }
}
