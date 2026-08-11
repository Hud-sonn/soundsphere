/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soundsphere.music.R
import com.soundsphere.music.models.MediaMetadata
import com.soundsphere.music.utils.ComposeToImage
import com.soundsphere.music.utils.ShareCardDesign
import com.soundsphere.music.utils.ShareCardThemeColors

/**
 * Preview dialog for the generated track share card. Lets the user switch
 * between the available [ShareCardDesign] styles and share the final image.
 */
@Composable
fun ShareCardDialog(
    mediaMetadata: MediaMetadata,
    onDismiss: () -> Unit,
    onShare: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var selectedDesign by remember { mutableStateOf(ShareCardDesign.CLASSIC) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val scheme = MaterialTheme.colorScheme
    val themeColors =
        remember(scheme) {
            ShareCardThemeColors(
                primary = scheme.primary.toArgb(),
                onPrimary = scheme.onPrimary.toArgb(),
                secondary = scheme.secondary.toArgb(),
                tertiary = scheme.tertiary.toArgb(),
                surface = scheme.surface.toArgb(),
                onSurface = scheme.onSurface.toArgb(),
                onSurfaceVariant = scheme.onSurfaceVariant.toArgb(),
                surfaceContainer = scheme.surfaceContainer.toArgb(),
            )
        }

    LaunchedEffect(mediaMetadata, selectedDesign, themeColors) {
        previewBitmap =
            ComposeToImage.createShareCard(
                context = context,
                coverArtUrl = mediaMetadata.thumbnailUrl,
                songTitle = mediaMetadata.title,
                artistName = mediaMetadata.artists.joinToString { it.name },
                design = selectedDesign,
                themeColors = themeColors,
            )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.share_card_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(Modifier.height(16.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp)),
                ) {
                    val bitmap = previewBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.share_card_preview_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }

                Spacer(Modifier.height(16.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShareCardDesign.entries.forEach { design ->
                        FilterChip(
                            selected = design == selectedDesign,
                            onClick = { selectedDesign = design },
                            label = { Text(stringResource(shareCardDesignLabel(design))) },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Button(
                        onClick = { previewBitmap?.let(onShare) },
                        enabled = previewBitmap != null,
                    ) {
                        Text(stringResource(R.string.share_card_share))
                    }
                }
            }
        }
    }
}

@Composable
private fun shareCardDesignLabel(design: ShareCardDesign): Int =
    when (design) {
        ShareCardDesign.CLASSIC -> R.string.share_card_design_classic
        ShareCardDesign.VIBRANT -> R.string.share_card_design_vibrant
        ShareCardDesign.GRADIENT -> R.string.share_card_design_gradient
        ShareCardDesign.MINIMAL -> R.string.share_card_design_minimal
        ShareCardDesign.FRAMED -> R.string.share_card_design_framed
    }
