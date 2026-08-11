/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.soundsphere.music.R
import com.soundsphere.music.ui.component.LyricsBackgroundStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SHARE_CARD_SIZE = 1080

/**
 * Visual styles available for the generated track share card.
 */
enum class ShareCardDesign { CLASSIC, VIBRANT, GRADIENT, MINIMAL, FRAMED }

/**
 * Theme-derived colors used when rendering a share card, so every design
 * stays consistent with the app's Material color scheme.
 */
data class ShareCardThemeColors(
    val primary: Int,
    val onPrimary: Int,
    val secondary: Int,
    val tertiary: Int,
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val surfaceContainer: Int,
)

object ComposeToImage {
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.SOLID,
        textColor: Int? = null,
        secondaryTextColor: Int? = null,
        lyricsAlignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
    ): Bitmap =
        withContext(Dispatchers.Default) {
            // Use fixed high resolution as requested (2160x2160)
            // This ensures consistent high-quality output regardless of the device screen
            val imageWidth = 2160
            val imageHeight = 2160

            val bitmap = createBitmap(imageWidth, imageHeight)
            val canvas = Canvas(bitmap)

            val defaultBackgroundColor = 0xFF121212.toInt()
            val defaultTextColor = 0xFFFFFFFF.toInt()
            val defaultSecondaryTextColor = 0xB3FFFFFF.toInt()

            val bgColor = backgroundColor ?: defaultBackgroundColor
            val mainTextColor = textColor ?: defaultTextColor
            val secondaryTxtColor = secondaryTextColor ?: defaultSecondaryTextColor

            // Pre-load cover art if needed for Blur/Gradient or just for the header
            var coverArtBitmap: Bitmap? = null
            if (coverArtUrl != null) {
                try {
                    val imageLoader = ImageLoader(context)
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(coverArtUrl)
                            .size(1024)
                            .allowHardware(false)
                            .build()
                    val result = imageLoader.execute(request)
                    coverArtBitmap = result.image?.toBitmap()
                } catch (_: Exception) {
                }
            }

            // Draw Background
            val backgroundRect = RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())
            val backgroundPaint =
                Paint().apply {
                    isAntiAlias = true
                }

            when (backgroundStyle) {
                LyricsBackgroundStyle.SOLID -> {
                    backgroundPaint.color = bgColor
                    canvas.drawRect(backgroundRect, backgroundPaint)
                }

                LyricsBackgroundStyle.BLUR -> {
                    // Draw black base
                    backgroundPaint.color = 0xFF000000.toInt()
                    canvas.drawRect(backgroundRect, backgroundPaint)

                    if (coverArtBitmap != null) {
                        try {
                            // Create a scaled down version for blurring (performance)
                            val scaledBitmap = Bitmap.createScaledBitmap(coverArtBitmap, imageWidth / 10, imageHeight / 10, true)
                            val blurredBitmap = fastBlur(scaledBitmap, 1f, 20) // Radius 20 on small image is large blur

                            if (blurredBitmap != null) {
                                val blurRect = RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())
                                canvas.drawBitmap(blurredBitmap, null, blurRect, null)

                                // Dark overlay for readability
                                val overlayPaint =
                                    Paint().apply {
                                        color = 0x4D000000 // 30% black overlay
                                    }
                                canvas.drawRect(blurRect, overlayPaint)
                            }
                        } catch (e: Exception) {
                            // Fallback to solid
                            backgroundPaint.color = bgColor
                            canvas.drawRect(backgroundRect, backgroundPaint)
                        }
                    } else {
                        backgroundPaint.color = bgColor
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    }
                }

                LyricsBackgroundStyle.GRADIENT -> {
                    if (coverArtBitmap != null) {
                        val palette = Palette.from(coverArtBitmap).generate()
                        val vibrant = palette.getVibrantColor(bgColor)
                        val darkVibrant = palette.getDarkVibrantColor(bgColor)

                        val gradient =
                            LinearGradient(
                                0f,
                                0f,
                                imageWidth.toFloat(),
                                imageHeight.toFloat(),
                                intArrayOf(vibrant, darkVibrant),
                                null,
                                Shader.TileMode.CLAMP,
                            )
                        backgroundPaint.shader = gradient
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    } else {
                        backgroundPaint.color = bgColor
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    }
                }
            }

            // Base scale on width relative to the reference design (340dp)
            // 2160 / 340 ≈ 6.35
            val scale = imageWidth / 340f

            val cornerRadius = 20f * scale

            // Draw inner border
            val borderPaint =
                Paint().apply {
                    color = mainTextColor
                    alpha = (255 * 0.09).toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 1f * scale
                    isAntiAlias = true
                }
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, borderPaint)

            val padding = 28f * scale

            // --- Header Section ---
            val coverArtSize = 64f * scale
            val headerBottomPadding = 12f * scale

            val coverCornerRadius = 3f * scale
            coverArtBitmap?.let {
                val rect = RectF(padding, padding, padding + coverArtSize, padding + coverArtSize)
                val path =
                    Path().apply {
                        addRoundRect(rect, coverCornerRadius, coverCornerRadius, Path.Direction.CW)
                    }

                // Draw border for cover art
                val coverBorderPaint =
                    Paint().apply {
                        color = mainTextColor
                        alpha = (255 * 0.16).toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 1f * scale
                        isAntiAlias = true
                    }

                canvas.withClip(path) {
                    drawBitmap(it, null, rect, null)
                }
                canvas.drawRoundRect(rect, coverCornerRadius, coverCornerRadius, coverBorderPaint)
            }

            val textStartX = padding + coverArtSize + (16f * scale)
            val textMaxWidth = imageWidth - textStartX - padding

            val titlePaint =
                TextPaint().apply {
                    color = mainTextColor
                    textSize = 20f * scale
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }

            val artistPaint =
                TextPaint().apply {
                    color = secondaryTxtColor
                    textSize = 16f * scale
                    typeface = Typeface.DEFAULT
                    isAntiAlias = true
                }

            val titleLayout =
                StaticLayout.Builder
                    .obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(1)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()

            val artistLayout =
                StaticLayout.Builder
                    .obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(1)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()

            // Vertically align text block with cover art
            val headerTextHeight = titleLayout.height + artistLayout.height + (2f * scale) // +2dp padding between title and artist
            val headerCenterY = padding + coverArtSize / 2f
            val titleY = headerCenterY - headerTextHeight / 2f

            canvas.save()
            canvas.translate(textStartX, titleY)
            titleLayout.draw(canvas)
            canvas.translate(0f, titleLayout.height.toFloat() + (2f * scale))
            artistLayout.draw(canvas)
            canvas.restore()

            // --- Footer Section ---
            val logoBoxSize = 22f * scale
            val logoIconSize = 16f * scale
            val footerY = imageHeight - padding - logoBoxSize

            // Draw Logo Background Box
            val logoBgPaint =
                Paint().apply {
                    color = secondaryTxtColor
                    isAntiAlias = true
                }
            val logoBoxRect = RectF(padding, footerY, padding + logoBoxSize, footerY + logoBoxSize)
            // Since it's a circle in preview: .clip(RoundedCornerShape(50)) which is usually circle for square box
            canvas.drawOval(logoBoxRect, logoBgPaint)

            // Draw Logo Icon
            val rawLogo = ContextCompat.getDrawable(context, R.drawable.small_icon)?.toBitmap()
            rawLogo?.let {
                val logoPaint =
                    Paint().apply {
                        isAntiAlias = true
                    }

                // Center logo in box
                val logoOffset = (logoBoxSize - logoIconSize) / 2f
                val logoRect =
                    RectF(
                        padding + logoOffset,
                        footerY + logoOffset,
                        padding + logoBoxSize - logoOffset,
                        footerY + logoBoxSize - logoOffset,
                    )
                canvas.drawBitmap(it, null, logoRect, logoPaint)
            }

            // Draw App Name
            val appName = context.getString(R.string.app_name)
            val appNamePaint =
                TextPaint().apply {
                    color = secondaryTxtColor
                    textSize = 14f * scale
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }

            val appNameX = padding + logoBoxSize + (8f * scale)
            // Center text vertically relative to logo box
            val appNameY = footerY + logoBoxSize / 2f - (appNamePaint.descent() + appNamePaint.ascent()) / 2f
            canvas.drawText(appName, appNameX, appNameY, appNamePaint)

            // --- Lyrics Section ---
            // Calculate available space
            val lyricsTop = padding + coverArtSize + headerBottomPadding
            val lyricsBottom = footerY - (12f * scale) // Add some padding above footer
            val lyricsHeight = lyricsBottom - lyricsTop
            val lyricsWidth = imageWidth - (padding * 2)

            val lyricsPaint =
                TextPaint().apply {
                    color = mainTextColor
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    letterSpacing = 0.005f
                }

            // Adaptive font size calculation
            // Start with a large size (e.g. 50sp equivalent) and scale down until it fits
            var lyricsTextSize = 50f * scale
            val minLyricsSize = 13f * scale
            var lyricsLayout: StaticLayout

            while (lyricsTextSize > minLyricsSize) {
                lyricsPaint.textSize = lyricsTextSize
                lyricsLayout =
                    StaticLayout.Builder
                        .obtain(lyrics, 0, lyrics.length, lyricsPaint, lyricsWidth.toInt())
                        .setAlignment(lyricsAlignment)
                        .setLineSpacing(0f, 1.2f)
                        .setIncludePad(false)
                        .build()

                if (lyricsLayout.height <= lyricsHeight) {
                    break
                }

                lyricsTextSize -= 1f * scale // Decrease by ~1sp equivalent steps
            }

            // One final rebuild with the determined size
            lyricsPaint.textSize = lyricsTextSize
            lyricsLayout =
                StaticLayout.Builder
                    .obtain(lyrics, 0, lyrics.length, lyricsPaint, lyricsWidth.toInt())
                    .setAlignment(lyricsAlignment)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(false)
                    .build()

            // Center vertically in the available space
            val lyricsContentHeight = lyricsLayout.height
            val lyricsY =
                if (lyricsContentHeight < lyricsHeight) {
                    lyricsTop + (lyricsHeight - lyricsContentHeight) / 2f
                } else {
                    lyricsTop
                }

            canvas.withTranslation(padding, lyricsY) {
                lyricsLayout.draw(this)
            }

            return@withContext bitmap
        }

    /**
     * Generates a 1080x1080 share card for a song:
     * blurred album-art background, rounded cover art, title/artist text
     * in the Soundsphere palette, a thin accent line and a corner wordmark.
     */
    suspend fun createShareCard(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        design: ShareCardDesign,
        themeColors: ShareCardThemeColors,
    ): Bitmap =
        withContext(Dispatchers.Default) {
            val bitmap = createBitmap(SHARE_CARD_SIZE, SHARE_CARD_SIZE)
            val canvas = Canvas(bitmap)

            val coverArtBitmap = loadCoverArt(context, coverArtUrl, 1024)

            when (design) {
                ShareCardDesign.CLASSIC -> drawClassicCard(canvas, context, coverArtBitmap, songTitle, artistName, themeColors)
                ShareCardDesign.VIBRANT -> drawVibrantCard(canvas, context, coverArtBitmap, songTitle, artistName, themeColors)
                ShareCardDesign.GRADIENT -> drawGradientCard(canvas, context, coverArtBitmap, songTitle, artistName, themeColors)
                ShareCardDesign.MINIMAL -> drawMinimalCard(canvas, context, coverArtBitmap, songTitle, artistName, themeColors)
                ShareCardDesign.FRAMED -> drawFramedCard(canvas, context, coverArtBitmap, songTitle, artistName, themeColors)
            }

            bitmap
        }

    /**
     * Classic design: blurred album-art background, rounded cover art, title
     * and artist in the Soundsphere cream palette, a thin accent line and a
     * corner wordmark.
     */
    private fun drawClassicCard(
        canvas: Canvas,
        context: Context,
        coverArtBitmap: Bitmap?,
        songTitle: String,
        artistName: String,
        themeColors: ShareCardThemeColors,
    ) {
        val imageWidth = SHARE_CARD_SIZE
        val imageHeight = SHARE_CARD_SIZE
        val titleColor = 0xFFEAE0D5.toInt()
        val artistColor = 0xFFC6AC8F.toInt()
        val accentColor = themeColors.primary
        val fallbackBackground = 0xFF17140F.toInt()

            val backgroundRect = RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())
            val backgroundPaint =
                Paint().apply {
                    isAntiAlias = true
                }

            if (coverArtBitmap != null) {
                try {
                    // Downscale before blurring for performance, then upscale via draw
                    val scaledBitmap = Bitmap.createScaledBitmap(coverArtBitmap, imageWidth / 12, imageHeight / 12, true)
                    val blurredBitmap = fastBlur(scaledBitmap, 1f, 24)

                    if (blurredBitmap != null) {
                        canvas.drawBitmap(blurredBitmap, null, backgroundRect, null)
                        // Dark overlay for readability
                        val overlayPaint =
                            Paint().apply {
                                color = 0x66000000 // 40% black overlay
                            }
                        canvas.drawRect(backgroundRect, overlayPaint)
                    } else {
                        backgroundPaint.color = fallbackBackground
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    }
                } catch (_: Exception) {
                    backgroundPaint.color = fallbackBackground
                    canvas.drawRect(backgroundRect, backgroundPaint)
                }
            } else {
                backgroundPaint.color = fallbackBackground
                canvas.drawRect(backgroundRect, backgroundPaint)
            }

            // --- Cover art ---
            val artSize = 560f
            val artCornerRadius = 28f
            val artLeft = (imageWidth - artSize) / 2f
            val artTop = 140f

            coverArtBitmap?.let { art ->
                val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
                val artPath =
                    Path().apply {
                        addRoundRect(artRect, artCornerRadius, artCornerRadius, Path.Direction.CW)
                    }

                canvas.withClip(artPath) {
                    drawBitmap(art, null, artRect, null)
                }

                // Subtle border around the cover art
                val borderPaint =
                    Paint().apply {
                        color = titleColor
                        alpha = (255 * 0.14).toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        isAntiAlias = true
                    }
                canvas.drawRoundRect(artRect, artCornerRadius, artCornerRadius, borderPaint)
            } ?: run {
                val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
                val artPath =
                    Path().apply {
                        addRoundRect(artRect, artCornerRadius, artCornerRadius, Path.Direction.CW)
                    }
                val placeholderPaint =
                    Paint().apply {
                        color = accentColor
                        alpha = (255 * 0.35).toInt()
                        isAntiAlias = true
                    }
                canvas.withClip(artPath) {
                    drawRect(artRect, placeholderPaint)
                }
            }

            // --- Labels ---
            val textMaxWidth = imageWidth - 160f
            val titlePaint =
                TextPaint().apply {
                    color = titleColor
                    textSize = 72f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }

            val artistPaint =
                TextPaint().apply {
                    color = artistColor
                    textSize = 44f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    isAntiAlias = true
                }

            // Adaptive title sizing so long titles still fit
            var titleTextSize = 72f
            val minTitleSize = 34f
            var titleLayout: StaticLayout

            while (titleTextSize > minTitleSize) {
                titlePaint.textSize = titleTextSize
                titleLayout =
                    StaticLayout.Builder
                        .obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setMaxLines(2)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END)
                        .setIncludePad(false)
                        .build()

                if (titleLayout.height <= 170f) {
                    break
                }

                titleTextSize -= 3f
            }

            titlePaint.textSize = titleTextSize
            titleLayout =
                StaticLayout.Builder
                    .obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .build()

            val artistLayout =
                StaticLayout.Builder
                    .obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setMaxLines(2)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .build()

            val labelsTop = artTop + artSize + 56f
            val totalTextHeight = titleLayout.height + 18f + artistLayout.height
            val labelsStartY = labelsTop + (260f - totalTextHeight) / 2f

            canvas.save()
            canvas.translate(80f, labelsStartY)
            titleLayout.draw(canvas)
            canvas.translate(0f, titleLayout.height.toFloat() + 18f)
            artistLayout.draw(canvas)
            canvas.restore()

            // --- Accent line ---
            val accentPaint =
                Paint().apply {
                    color = accentColor
                    isAntiAlias = true
                }
            val accentWidth = 150f
            val accentHeight = 5f
            val accentY = imageHeight - 150f
            canvas.drawRoundRect(
                RectF(
                    (imageWidth - accentWidth) / 2f,
                    accentY,
                    (imageWidth + accentWidth) / 2f,
                    accentY + accentHeight,
                ),
                2.5f,
                2.5f,
                accentPaint,
            )

            // --- Wordmark ---
            val wordmark = context.getString(R.string.app_name)
            val wordmarkPaint =
                TextPaint().apply {
                    color = accentColor
                    textSize = 34f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                    letterSpacing = 0.08f
                }
            val wordmarkLayout =
                StaticLayout.Builder
                    .obtain(wordmark, 0, wordmark.length, wordmarkPaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .build()
            canvas.save()
            canvas.translate(0f, imageHeight - 96f)
            wordmarkLayout.draw(canvas)
            canvas.restore()

    }

    /**
     * Vibrant design: saturated colors extracted from the album art,
     * full-bleed square artwork and bold typography.
     */
    private fun drawVibrantCard(
        canvas: Canvas,
        context: Context,
        coverArtBitmap: Bitmap?,
        songTitle: String,
        artistName: String,
        themeColors: ShareCardThemeColors,
    ) {
        val palette = coverArtBitmap?.let { Palette.from(it).generate() }
        val background =
            palette?.vibrantSwatch?.rgb
                ?: palette?.darkVibrantSwatch?.rgb
                ?: themeColors.primary
        val backgroundPaint =
            Paint().apply {
                isAntiAlias = true
                color = darken(background, 0.82f)
            }
        canvas.drawRect(RectF(0f, 0f, SHARE_CARD_SIZE.toFloat(), SHARE_CARD_SIZE.toFloat()), backgroundPaint)

        val textColor =
            palette?.lightVibrantSwatch?.rgb
                ?: palette?.lightMutedSwatch?.rgb
                ?: 0xFFFFFFFF.toInt()

        val artSize = 660f
        val artLeft = (SHARE_CARD_SIZE - artSize) / 2f
        val artTop = 120f
        drawRoundedArt(
            canvas = canvas,
            art = coverArtBitmap,
            left = artLeft,
            top = artTop,
            size = artSize,
            cornerRadius = 0f,
            borderColor = textColor,
            borderWidth = 10f,
            placeholderColor = palette?.mutedSwatch?.rgb ?: themeColors.secondary,
        )

        val titlePaint =
            TextPaint().apply {
                color = textColor
                textSize = 92f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
        val artistPaint =
            TextPaint().apply {
                color = textColor
                alpha = (255 * 0.72).toInt()
                textSize = 48f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
        val titleLayout =
            adaptiveLayout(songTitle, titlePaint, maxWidth = SHARE_CARD_SIZE - 160, maxLines = 2, minTextSize = 40f, maxTextHeight = 200f)
        val artistLayout =
            adaptiveLayout(artistName, artistPaint, maxWidth = SHARE_CARD_SIZE - 160, maxLines = 2, minTextSize = 30f, maxTextHeight = 130f)

        val labelsTop = artTop + artSize + 56f
        val totalTextHeight = titleLayout.height + 16f + artistLayout.height
        val labelsStartY = labelsTop + (300f - totalTextHeight) / 2f

        canvas.save()
        canvas.translate(80f, labelsStartY)
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height.toFloat() + 16f)
        artistLayout.draw(canvas)
        canvas.restore()

        drawWordmark(canvas, context, textColor, y = SHARE_CARD_SIZE - 96f)
    }

    /**
     * Gradient design: diagonal primary-to-tertiary gradient background with
     * a bordered rounded cover and white typography.
     */
    private fun drawGradientCard(
        canvas: Canvas,
        context: Context,
        coverArtBitmap: Bitmap?,
        songTitle: String,
        artistName: String,
        themeColors: ShareCardThemeColors,
    ) {
        val gradient =
            LinearGradient(
                0f,
                0f,
                SHARE_CARD_SIZE.toFloat(),
                SHARE_CARD_SIZE.toFloat(),
                intArrayOf(themeColors.primary, themeColors.secondary, themeColors.tertiary),
                null,
                Shader.TileMode.CLAMP,
            )
        val backgroundPaint =
            Paint().apply {
                isAntiAlias = true
                shader = gradient
            }
        canvas.drawRect(RectF(0f, 0f, SHARE_CARD_SIZE.toFloat(), SHARE_CARD_SIZE.toFloat()), backgroundPaint)

        val artSize = 600f
        val artLeft = (SHARE_CARD_SIZE - artSize) / 2f
        val artTop = 140f
        drawRoundedArt(
            canvas = canvas,
            art = coverArtBitmap,
            left = artLeft,
            top = artTop,
            size = artSize,
            cornerRadius = 32f,
            borderColor = 0xFFFFFFFF.toInt(),
            borderWidth = 8f,
            placeholderColor = 0x33FFFFFF,
        )

        val titlePaint =
            TextPaint().apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 84f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
        val artistPaint =
            TextPaint().apply {
                color = 0xFFFFFFFF.toInt()
                alpha = (255 * 0.85).toInt()
                textSize = 46f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
        val titleLayout =
            adaptiveLayout(songTitle, titlePaint, maxWidth = SHARE_CARD_SIZE - 160, maxLines = 2, minTextSize = 38f, maxTextHeight = 190f)
        val artistLayout =
            adaptiveLayout(artistName, artistPaint, maxWidth = SHARE_CARD_SIZE - 160, maxLines = 2, minTextSize = 30f, maxTextHeight = 130f)

        val labelsTop = artTop + artSize + 56f
        val totalTextHeight = titleLayout.height + 16f + artistLayout.height
        val labelsStartY = labelsTop + (280f - totalTextHeight) / 2f

        canvas.save()
        canvas.translate(80f, labelsStartY)
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height.toFloat() + 16f)
        artistLayout.draw(canvas)
        canvas.restore()

        drawWordmark(canvas, context, 0xCCFFFFFF.toInt(), y = SHARE_CARD_SIZE - 96f)
    }

    /**
     * Minimal design: solid theme surface, compact rounded artwork and
     * restrained typography with a primary accent divider.
     */
    private fun drawMinimalCard(
        canvas: Canvas,
        context: Context,
        coverArtBitmap: Bitmap?,
        songTitle: String,
        artistName: String,
        themeColors: ShareCardThemeColors,
    ) {
        val backgroundPaint =
            Paint().apply {
                isAntiAlias = true
                color = themeColors.surface
            }
        canvas.drawRect(RectF(0f, 0f, SHARE_CARD_SIZE.toFloat(), SHARE_CARD_SIZE.toFloat()), backgroundPaint)

        val artSize = 460f
        val artLeft = (SHARE_CARD_SIZE - artSize) / 2f
        val artTop = 160f
        drawRoundedArt(
            canvas = canvas,
            art = coverArtBitmap,
            left = artLeft,
            top = artTop,
            size = artSize,
            cornerRadius = 24f,
            placeholderColor = themeColors.primary,
        )

        val titlePaint =
            TextPaint().apply {
                color = themeColors.onSurface
                textSize = 78f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
        val artistPaint =
            TextPaint().apply {
                color = themeColors.onSurfaceVariant
                textSize = 46f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
        val titleLayout =
            adaptiveLayout(songTitle, titlePaint, maxWidth = SHARE_CARD_SIZE - 160, maxLines = 2, minTextSize = 36f, maxTextHeight = 190f)
        val artistLayout =
            adaptiveLayout(artistName, artistPaint, maxWidth = SHARE_CARD_SIZE - 160, maxLines = 2, minTextSize = 28f, maxTextHeight = 130f)

        val labelsTop = artTop + artSize + 64f
        val totalTextHeight = titleLayout.height + 18f + 6f + artistLayout.height
        val labelsStartY = labelsTop + (280f - totalTextHeight) / 2f

        canvas.save()
        canvas.translate(80f, labelsStartY)
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height.toFloat() + 18f)
        val dividerPaint =
            Paint().apply {
                color = themeColors.primary
                isAntiAlias = true
            }
        canvas.drawRoundRect(
            RectF((SHARE_CARD_SIZE - 80f) / 2f, 0f, (SHARE_CARD_SIZE + 80f) / 2f, 5f),
            2.5f,
            2.5f,
            dividerPaint,
        )
        canvas.translate(0f, 11f)
        artistLayout.draw(canvas)
        canvas.restore()

        drawWordmark(canvas, context, themeColors.primary, y = SHARE_CARD_SIZE - 96f)
    }

    /**
     * Framed design: surface-container background with a primary-bordered
     * cover, on-surface title and primary artist name.
     */
    private fun drawFramedCard(
        canvas: Canvas,
        context: Context,
        coverArtBitmap: Bitmap?,
        songTitle: String,
        artistName: String,
        themeColors: ShareCardThemeColors,
    ) {
        val backgroundPaint =
            Paint().apply {
                isAntiAlias = true
                color = themeColors.surfaceContainer
            }
        canvas.drawRect(RectF(0f, 0f, SHARE_CARD_SIZE.toFloat(), SHARE_CARD_SIZE.toFloat()), backgroundPaint)

        val artSize = 640f
        val artLeft = (SHARE_CARD_SIZE - artSize) / 2f
        val artTop = 110f
        drawRoundedArt(
            canvas = canvas,
            art = coverArtBitmap,
            left = artLeft,
            top = artTop,
            size = artSize,
            cornerRadius = 44f,
            borderColor = themeColors.primary,
            borderWidth = 14f,
            placeholderColor = themeColors.primary,
        )

        val titlePaint =
            TextPaint().apply {
                color = themeColors.onSurface
                textSize = 76f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
        val artistPaint =
            TextPaint().apply {
                color = themeColors.primary
                textSize = 44f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
        val titleLayout =
            adaptiveLayout(songTitle, titlePaint, maxWidth = SHARE_CARD_SIZE - 160, maxLines = 2, minTextSize = 34f, maxTextHeight = 190f)
        val artistLayout =
            adaptiveLayout(artistName, artistPaint, maxWidth = SHARE_CARD_SIZE - 160, maxLines = 2, minTextSize = 28f, maxTextHeight = 130f)

        val labelsTop = artTop + artSize + 56f
        val totalTextHeight = titleLayout.height + 14f + artistLayout.height
        val labelsStartY = labelsTop + (260f - totalTextHeight) / 2f

        canvas.save()
        canvas.translate(80f, labelsStartY)
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height.toFloat() + 14f)
        artistLayout.draw(canvas)
        canvas.restore()

        drawWordmark(canvas, context, themeColors.onSurfaceVariant, y = SHARE_CARD_SIZE - 96f)
    }

    private suspend fun loadCoverArt(context: Context, coverArtUrl: String?, size: Int): Bitmap? {
        if (coverArtUrl == null) return null
        return try {
            val imageLoader = ImageLoader(context)
            val request =
                ImageRequest
                    .Builder(context)
                    .data(coverArtUrl)
                    .size(size)
                    .allowHardware(false)
                    .build()
            val result = imageLoader.execute(request)
            result.image?.toBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun adaptiveLayout(
        text: String,
        paint: TextPaint,
        maxWidth: Int,
        maxLines: Int,
        minTextSize: Float,
        maxTextHeight: Float,
        align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
    ): StaticLayout {
        var textSize = paint.textSize
        while (textSize > minTextSize) {
            paint.textSize = textSize
            val layout =
                StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, maxWidth)
                    .setAlignment(align)
                    .setMaxLines(maxLines)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .setIncludePad(false)
                    .build()
            if (layout.height <= maxTextHeight) {
                break
            }
            textSize -= 3f
        }
        paint.textSize = textSize
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(align)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }

    private fun drawRoundedArt(
        canvas: Canvas,
        art: Bitmap?,
        left: Float,
        top: Float,
        size: Float,
        cornerRadius: Float,
        borderColor: Int? = null,
        borderWidth: Float = 0f,
        placeholderColor: Int? = null,
    ) {
        val artRect = RectF(left, top, left + size, top + size)
        val artPath =
            Path().apply {
                addRoundRect(artRect, cornerRadius, cornerRadius, Path.Direction.CW)
            }

        if (art != null) {
            canvas.withClip(artPath) {
                drawBitmap(art, null, artRect, null)
            }
        } else if (placeholderColor != null) {
            val placeholderPaint =
                Paint().apply {
                    color = placeholderColor
                    alpha = (255 * 0.5).toInt()
                    isAntiAlias = true
                }
            canvas.withClip(artPath) {
                drawRect(artRect, placeholderPaint)
            }
        }

        if (borderColor != null && borderWidth > 0f) {
            val borderPaint =
                Paint().apply {
                    color = borderColor
                    style = Paint.Style.STROKE
                    strokeWidth = borderWidth
                    isAntiAlias = true
                }
            canvas.drawRoundRect(artRect, cornerRadius, cornerRadius, borderPaint)
        }
    }

    private fun drawWordmark(
        canvas: Canvas,
        context: Context,
        color: Int,
        y: Float,
    ) {
        val wordmark = context.getString(R.string.app_name)
        val wordmarkPaint =
            TextPaint().apply {
                this.color = color
                textSize = 34f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = 0.08f
            }
        val wordmarkLayout =
            StaticLayout.Builder
                .obtain(wordmark, 0, wordmark.length, wordmarkPaint, SHARE_CARD_SIZE - 160)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
        canvas.save()
        canvas.translate(0f, y)
        wordmarkLayout.draw(canvas)
        canvas.restore()
    }

    private fun darken(color: Int, factor: Float): Int {
        val a = color ushr 24 and 0xFF
        val r = ((color ushr 16 and 0xFF) * factor).roundToInt()
        val g = ((color ushr 8 and 0xFF) * factor).roundToInt()
        val b = ((color and 0xFF) * factor).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    // Stack Blur v1.0 from http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
    // Java Author: Mario Klingemann <mario at quasimondo.com>
    // http://incubator.quasimondo.com
    //
    // created Feburary 29, 2004
    // Android port : Yahel Bouaziz <yahel at kayenko.com>
    // http://www.kayenko.com
    // ported to Kotlin and adapted
    private fun fastBlur(
        sentBitmap: Bitmap,
        scale: Float,
        radius: Int,
    ): Bitmap? {
        val width = (sentBitmap.width * scale).roundToInt()
        val height = (sentBitmap.height * scale).roundToInt()

        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false)
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))
        var divsum = div + 1 shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }
        yw = 0
        yi = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        var r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                sir = stack[i + radius]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = Math.max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w
                }
                p = x + vmin[y]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    fun saveBitmapAsFile(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Soundsphere")
                }
            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ) ?: throw IllegalStateException("Failed to create new MediaStore record")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            uri
        } else {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                imageFile,
            )
        }
}
