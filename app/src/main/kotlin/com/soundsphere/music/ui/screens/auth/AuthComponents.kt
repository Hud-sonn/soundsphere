/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundsphere.music.R
import com.soundsphere.music.ui.theme.hankenGrotesk

/** Shared Material color scheme for auth text fields. */
@Composable
private fun authFieldColors() = run {
    val c = rememberEarthyAuthColors()
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.primaryText,
        unfocusedBorderColor = c.secondaryText,
        focusedContainerColor = c.surface,
        unfocusedContainerColor = c.surface,
        cursorColor = c.secondaryText,
        focusedTextColor = c.primaryText,
        unfocusedTextColor = c.primaryText,
        focusedPlaceholderColor = c.secondaryText.copy(alpha = 0.4f),
        unfocusedPlaceholderColor = c.secondaryText.copy(alpha = 0.4f),
        focusedLabelColor = c.secondaryText,
        unfocusedLabelColor = c.secondaryText,
    )
}

@Composable
fun AuthLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    val c = rememberEarthyAuthColors()

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = c.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(c.surface, RoundedCornerShape(8.dp))
                    .border(1.dp, c.secondaryText, RoundedCornerShape(8.dp)),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = c.secondaryText.copy(alpha = 0.4f)) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                visualTransformation =
                    if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                colors = authFieldColors(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = hankenGrotesk,
                    fontSize = 16.sp,
                    color = c.primaryText,
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                trailingIcon = {
                    if (isPassword) {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                painter = painterResource(
                                    if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                ),
                                contentDescription = null,
                                tint = c.secondaryText.copy(alpha = 0.6f),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun AuthFooter(
    prompt: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val c = rememberEarthyAuthColors()
    Row(
        modifier = Modifier.padding(top = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prompt, color = c.outline, fontSize = 14.sp)
        Spacer(Modifier.size(4.dp))
        Text(
            text = actionLabel,
            color = c.secondaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onAction),
        )
    }
}

/** Fixed-height pill CTA used across auth screens. */
@Composable
fun AuthPillButton(
    label: String,
    isLoading: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = rememberEarthyAuthColors()
    androidx.compose.material3.Button(
        onClick = onTap,
        enabled = !isLoading,
        shape = RoundedCornerShape(50),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.accent),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = c.primaryText,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = label,
                color = c.primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
            )
        }
    }
}
