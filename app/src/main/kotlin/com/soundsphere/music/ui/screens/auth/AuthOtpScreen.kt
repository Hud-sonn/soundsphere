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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
fun AuthOtpScreen(
    email: String,
    isLoading: Boolean,
    error: String?,
    onVerify: (otp: String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    var otp by remember { mutableStateOf("") }
    val c = EarthyAuthColors

    Box(
        modifier = Modifier.fillMaxSize().background(c.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Check Your Email",
                color = c.primaryText,
                fontFamily = hankenGrotesk,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "We sent a 6-digit code to $email",
                color = c.secondaryText,
                fontFamily = hankenGrotesk,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                placeholder = { Text("------", color = c.secondaryText.copy(alpha = 0.4f)) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = hankenGrotesk,
                    fontSize = 24.sp,
                    color = c.primaryText,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.primaryText,
                    unfocusedBorderColor = c.secondaryText,
                    focusedContainerColor = c.surface,
                    unfocusedContainerColor = c.surface,
                    cursorColor = c.secondaryText,
                    focusedTextColor = c.primaryText,
                    unfocusedTextColor = c.primaryText,
                    focusedPlaceholderColor = c.secondaryText.copy(alpha = 0.4f),
                    unfocusedPlaceholderColor = c.secondaryText.copy(alpha = 0.4f),
                ),
                modifier = Modifier.fillMaxWidth().height(64.dp),
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
                label = "VERIFY",
                isLoading = isLoading,
                onTap = { onVerify(otp) },
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Didn't get it? Resend",
                color = c.secondaryText,
                fontFamily = hankenGrotesk,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onResend),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "← Back",
                color = c.outline,
                fontFamily = hankenGrotesk,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onBack),
            )
        }
    }
}
