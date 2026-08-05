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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundsphere.music.R
import com.soundsphere.music.utils.dimenResource
import com.soundsphere.music.ui.theme.hankenGrotesk

@Composable
fun AuthLoginScreen(
    isLoading: Boolean,
    error: String?,
    onLogin: (email: String, password: String) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val c = EarthyAuthColors

    Box(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo mark
            SplashLogoMark(
                boxSize = dimenResource(R.dimen.logo_size_auth),
                markSize = dimenResource(R.dimen.logo_size_auth_mark),
                containerColor = c.surface,
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Welcome Back",
                color = c.primaryText,
                fontFamily = hankenGrotesk,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "The music missed you.",
                color = c.secondaryText,
                fontFamily = hankenGrotesk,
                fontSize = 18.sp,
                fontStyle = FontStyle.Italic,
            )

            Spacer(Modifier.height(40.dp))

            AuthLabeledField(
                label = "EMAIL ADDRESS",
                value = email,
                onValueChange = { email = it },
                placeholder = "your@email.com",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(16.dp))

            AuthLabeledField(
                label = "PASSWORD",
                value = password,
                onValueChange = { password = it },
                placeholder = "••••••••",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isPassword = true,
            )

            // Forgot password link
            Text(
                text = "Forgot Password?",
                color = c.secondaryText,
                fontFamily = hankenGrotesk,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clickable { onForgotPassword() },
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
                label = "LOG IN",
                isLoading = isLoading,
                onTap = { onLogin(email.trim(), password) },
            )

            Spacer(Modifier.height(24.dp))

            AuthFooter(
                prompt = "New here?",
                actionLabel = "Create Account",
                onAction = onCreateAccount,
            )
        }
    }
}
