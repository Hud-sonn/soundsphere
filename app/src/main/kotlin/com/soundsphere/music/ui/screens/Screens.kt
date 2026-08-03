/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.soundsphere.music.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.search,
        iconIdActive = R.drawable.search,
        route = "search_input"
    )

    object ListenTogether : Screens(
        titleId = R.string.together,
        iconIdInactive = R.drawable.group_outlined,
        iconIdActive = R.drawable.group_filled,
        route = "listen_together"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_music_outlined,
        iconIdActive = R.drawable.library_music_filled,
        route = "library"
    )

    object Splash : Screens(
        titleId = R.string.app_name,
        iconIdInactive = R.drawable.soundsphere_foreground_mark,
        iconIdActive = R.drawable.soundsphere_foreground_mark,
        route = "splash"
    )

    /**
     * SoundSphere account gate (register/login/OTP). Kept distinct from the
     * YouTube Music WebView login route so the two account systems never collide.
     */
    object Auth : Screens(
        titleId = R.string.auth_account_title,
        iconIdInactive = R.drawable.soundsphere_foreground_mark,
        iconIdActive = R.drawable.soundsphere_foreground_mark,
        route = "auth"
    )

    companion object {
        val MainScreens = listOf(Home, Search, ListenTogether, Library)
    }
}
