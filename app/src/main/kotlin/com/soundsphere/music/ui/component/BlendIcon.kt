/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The Blend mark — two overlapping aura circles, intersection lens, sound-wave
 * pulse, spark dots. Drawn on Canvas (not a static drawable) so every color
 * resolves from [MaterialTheme.colorScheme] and follows light/dark + user
 * themes. Geometry matches the 64×64 design spec, scaled to layout size.
 *
 * Reuse this everywhere a Blend needs an identity (tiles, placeholders,
 * headers, share cards) — do NOT recreate the artwork per call site.
 *
 * @param leftStroke left aura ring, @param leftFill its wash,
 * @param rightStroke right aura ring, @param rightFill its wash,
 * @param accent lens glow + wave + dots.
 */
@Composable
fun BlendIcon(
    modifier: Modifier = Modifier,
    leftStroke: Color = MaterialTheme.colorScheme.tertiary,
    leftFill: Color = MaterialTheme.colorScheme.tertiaryContainer,
    rightStroke: Color = MaterialTheme.colorScheme.primary,
    rightFill: Color = MaterialTheme.colorScheme.primaryContainer,
    accent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        },
    ) {
        val s = size.minDimension / 64f
        fun X(v: Float) = v * s

        // Left aura: wash then ring
        drawCircle(color = leftFill, radius = X(18f), center = center.copy(x = X(26f), y = X(32f)), alpha = 0.25f)
        drawCircle(
            color = leftStroke,
            radius = X(18f),
            center = center.copy(x = X(26f), y = X(32f)),
            alpha = 0.9f,
            style = Stroke(width = X(2f)),
        )
        // Right aura: wash then ring
        drawCircle(color = rightFill, radius = X(18f), center = center.copy(x = X(38f), y = X(32f)), alpha = 0.35f)
        drawCircle(
            color = rightStroke,
            radius = X(18f),
            center = center.copy(x = X(38f), y = X(32f)),
            alpha = 0.9f,
            style = Stroke(width = X(2f)),
        )
        // Intersection lens
        val lens = Path().apply {
            moveTo(X(32f), X(20.8f))
            cubicTo(X(34.5f), X(24f), X(36f), X(27.8f), X(36f), X(32f))
            cubicTo(X(36f), X(36.2f), X(34.5f), X(40f), X(32f), X(43.2f))
            cubicTo(X(29.5f), X(40f), X(28f), X(36.2f), X(28f), X(32f))
            cubicTo(X(28f), X(27.8f), X(29.5f), X(24f), X(32f), X(20.8f))
            close()
        }
        drawPath(path = lens, color = accent, alpha = 0.4f)
        drawPath(path = lens, color = accent, alpha = 0.9f, style = Stroke(width = X(1.5f)))
        // Sound-wave pulse
        val wave = Path().apply {
            moveTo(X(22f), X(32f))
            cubicTo(X(22f), X(28f), X(26f), X(28f), X(28f), X(32f))
            cubicTo(X(30f), X(36f), X(34f), X(36f), X(36f), X(32f))
            cubicTo(X(38f), X(28f), X(42f), X(28f), X(42f), X(32f))
        }
        drawPath(path = wave, color = accent, style = Stroke(width = X(2f), cap = StrokeCap.Round))
        // Spark dots
        drawCircle(color = accent, radius = X(1.5f), center = center.copy(x = X(32f), y = X(15f)))
        drawCircle(color = rightStroke, radius = X(1.5f), center = center.copy(x = X(32f), y = X(49f)), alpha = 0.9f)
    }
}
