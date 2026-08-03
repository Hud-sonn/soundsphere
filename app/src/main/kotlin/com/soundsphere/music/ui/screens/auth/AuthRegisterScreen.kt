/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundsphere.music.R
import com.soundsphere.music.ui.theme.hankenGrotesk

@Composable
fun AuthRegisterScreen(
    isLoading: Boolean,
    error: String?,
    onRegister: (username: String, email: String, password: String) -> Unit,
    onLogin: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
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
            Box(
                modifier = Modifier.size(dimensionResource(R.dimen.logo_size_auth)).background(c.surface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = stringResource(R.string.wrapped_logo_content_description),
                    tint = c.primaryText,
                    modifier = Modifier.size(dimensionResource(R.dimen.logo_size_auth_mark)),
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Start Listening",
                color = c.primaryText,
                fontFamily = hankenGrotesk,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Your sound journey begins here.",
                color = c.secondaryText,
                fontFamily = hankenGrotesk,
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
            )

            Spacer(Modifier.height(32.dp))

            AuthLabeledField(
                label = "USERNAME",
                value = username,
                onValueChange = { username = it },
                placeholder = "Choose a name",
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(16.dp))

            AuthLabeledField(
                label = "EMAIL ADDRESS",
                value = email,
                onValueChange = { email = it },
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(16.dp))

            AuthLabeledField(
                label = "PASSWORD",
                value = password,
                onValueChange = { password = it },
                placeholder = "Create a password",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isPassword = true,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "A verification code will be sent to your email.",
                color = c.secondaryText,
                fontFamily = hankenGrotesk,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    color = c.secondaryText,
                    fontFamily = hankenGrotesk,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            AuthPillButton(
                label = "CREATE ACCOUNT",
                isLoading = isLoading,
                onTap = { onRegister(username.trim(), email.trim(), password) },
            )

            Spacer(Modifier.height(24.dp))

            AuthFooter(
                prompt = "Already have an account?",
                actionLabel = "Log In",
                onAction = onLogin,
            )
        }
    }
}
