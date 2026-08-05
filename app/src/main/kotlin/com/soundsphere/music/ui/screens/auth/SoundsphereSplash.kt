/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soundsphere.music.R
import com.soundsphere.music.utils.dimenResource

/**
 * Shared branded logo mark: the Soundsphere S on a rounded surface tile.
 * Uses [R.drawable.ic_splash_logo] (a real VectorDrawable) instead of the
 * launcher icon resource, which is a <bitmap> wrapper that painterResource()
 * cannot decode on pre-Android-12 devices.
 */
@Composable
fun SplashLogoMark(
    boxSize: Dp,
    markSize: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    containerColor: Color = EarthyAuthColors.surface,
    tint: Color = Color.Unspecified,
) {
    Box(
        modifier =
            modifier
                .size(boxSize)
                .clip(RoundedCornerShape(cornerRadius))
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = stringResource(R.string.wrapped_logo_content_description),
            tint = tint,
            modifier = Modifier.size(markSize),
        )
    }
}

/**
 * Static brand splash visual (logo centered on the Soundsphere dark background).
 * Used as the nav Splash route while the initial auth check runs and as the
 * brief overlay after login while the home screen completes its initial sync.
 */
@Composable
fun SoundsphereSplashLogo(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(EarthyAuthColors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SplashLogoMark(
            boxSize = dimenResource(R.dimen.logo_size_splash),
            markSize = dimenResource(R.dimen.logo_size_splash_mark),
            cornerRadius = 24.dp,
        )
    }
}