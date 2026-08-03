/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.soundsphere.music.R
import kotlinx.coroutines.delay

@Composable
fun AuthSplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "splashProgress",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1600)
        onFinished()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(EarthyAuthColors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(dimenResource(R.dimen.logo_size_splash))
                        .clip(RoundedCornerShape(24.dp))
                        .background(EarthyAuthColors.surface),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.wrapped_logo_content_description),
                    tint = EarthyAuthColors.primaryText,
                    modifier = Modifier.size(dimenResource(R.dimen.logo_size_splash_mark)),
                )
            }
            // Thin 1px accent progress line
            Box(
                modifier =
                    Modifier
                        .padding(top = 32.dp)
                        .width(120.dp)
                        .height(1.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(EarthyAuthColors.outline.copy(alpha = 0.3f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(120.dp * progress)
                            .height(1.dp)
                            .background(EarthyAuthColors.secondaryText),
                )
            }
        }
    }
}
