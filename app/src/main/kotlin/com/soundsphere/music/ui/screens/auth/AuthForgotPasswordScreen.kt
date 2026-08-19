/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundsphere.music.ui.theme.hankenGrotesk

@Composable
fun AuthForgotPasswordScreen(
    isLoading: Boolean,
    error: String?,
    onSend: (email: String) -> Unit,
    onBack: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    val c = rememberEarthyAuthColors()

    Box(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Forgot Password?",
                color = c.primaryText,
                fontFamily = hankenGrotesk,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Enter your email and we'll send you a reset code.",
                color = c.secondaryText,
                fontFamily = hankenGrotesk,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            AuthLabeledField(
                label = "EMAIL ADDRESS",
                value = email,
                onValueChange = { email = it },
                placeholder = "your@email.com",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            )

            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = error,
                    color = c.secondaryText,
                    fontFamily = hankenGrotesk,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(28.dp))

            AuthPillButton(
                label = "SEND CODE",
                isLoading = isLoading,
                onTap = { onSend(email.trim()) },
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "← Back to Log In",
                color = c.outline,
                fontFamily = hankenGrotesk,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onBack),
            )
        }
    }
}
