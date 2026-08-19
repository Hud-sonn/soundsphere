package com.soundsphere.music.ui.screens.settings

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.soundsphere.music.R
import com.soundsphere.music.constants.DarkModeKey
import com.soundsphere.music.constants.DynamicThemeKey
import com.soundsphere.music.constants.PaletteMode
import com.soundsphere.music.constants.PaletteModeKey
import com.soundsphere.music.constants.ComboAccentColorKey
import com.soundsphere.music.constants.WallpaperBackgroundKey
import com.soundsphere.music.constants.WallpaperUriKey
import com.soundsphere.music.constants.PureBlackKey
import com.soundsphere.music.constants.PureBlackMiniPlayerKey
import com.soundsphere.music.constants.ThemeVariant
import com.soundsphere.music.constants.ThemeVariantKey
import com.soundsphere.music.ui.theme.extractThemeColor
import com.soundsphere.music.constants.SelectedThemeColorKey
import com.soundsphere.music.ui.theme.DefaultThemeColor
import com.soundsphere.music.ui.theme.SoundsphereTheme
import com.soundsphere.music.utils.rememberEnumPreference
import com.soundsphere.music.utils.rememberPreference

data class ThemePalette(
    val nameRes: Int,
    val seedColor: Color,
    val labelColor: Color = Color(0xFF0A0908), // Default dark label for most palettes
)

data class ColorCombo(
    val nameRes: Int,
    val primary: Color,
    val accent: Color,
    val surface: Color,
    val labelColor: Color = Color(0xFF0A0908),
)

val PaletteColors = listOf(
    ThemePalette(R.string.palette_dynamic, Color.Transparent), // Sentinel for System/Dynamic colors
    ThemePalette(R.string.palette_default_brand, Color(0xFF5E503F)), // Earthy brown
    ThemePalette(R.string.palette_acid_lime, Color(0xFFC0CA33), Color(0xFF0A0908)), // Lime
    ThemePalette(R.string.palette_teal_charcoal, Color(0xFF00897B), Color(0xFF424242)), // Teal + Charcoal
    ThemePalette(R.string.palette_crimson, Color(0xFFEC5464)), // Crimson
    ThemePalette(R.string.palette_rose, Color(0xFFD81B60)), // Rose
    ThemePalette(R.string.palette_purple, Color(0xFF8E24AA)), // Purple
    ThemePalette(R.string.palette_deep_purple, Color(0xFF5E35B1)), // Deep Purple
    ThemePalette(R.string.palette_indigo, Color(0xFF3949AB)), // Indigo
    ThemePalette(R.string.palette_blue, Color(0xFF1E88E5)), // Blue
    ThemePalette(R.string.palette_amber, Color(0xFFFFB300)), // Amber
    ThemePalette(R.string.palette_spotify_green, Color(0xFF1DB954)), // Spotify green
    ThemePalette(R.string.palette_cyan, Color(0xFF00ACC1)), // Cyan
    ThemePalette(R.string.palette_deep_orange, Color(0xFFF4511E)), // Deep Orange
    ThemePalette(R.string.palette_charcoal_grey, Color(0xFF424242)), // Charcoal Grey
    ThemePalette(R.string.palette_grey, Color(0xFF757575)), // Grey
    ThemePalette(R.string.palette_cool_grey, Color(0xFF9E9E9E)), // Cool Grey
    ThemePalette(R.string.palette_blue_grey, Color(0xFF546E7A)), // Blue Grey
    ThemePalette(R.string.palette_pure_black, Color(0xFF000000), Color(0xFFFFFFFF)), // Pure Black (white label)
)

val ColorCombos = listOf(
    // Movie poster blue + orange (muted complementary)
    ColorCombo(
        R.string.combo_ocean_sunset,
        primary = Color(0xFF264653),
        accent = Color(0xFFF4A261),
        surface = Color(0xFF1A2F3A),
    ),
    // Teal + coral (nature-inspired complementary)
    ColorCombo(
        R.string.combo_teal_coral,
        primary = Color(0xFF008080),
        accent = Color(0xFFFF6F61),
        surface = Color(0xFF0A1F1F),
    ),
    // Digital lavender + deep plum (2025 trend)
    ColorCombo(
        R.string.combo_lavender_plum,
        primary = Color(0xFFCEB4E1),
        accent = Color(0xFF4B0150),
        surface = Color(0xFF1A1020),
    ),
    // Midnight blue + electric mint (futuristic dark)
    ColorCombo(
        R.string.combo_midnight_mint,
        primary = Color(0xFF191970),
        accent = Color(0xFF00FFA3),
        surface = Color(0xFF0D0D3A),
    ),
    // Mustard + indigo (warm accent complementary)
    ColorCombo(
        R.string.combo_mustard_indigo,
        primary = Color(0xFFD4A017),
        accent = Color(0xFF3F37C9),
        surface = Color(0xFF1A1608),
    ),
    // Terracotta + sage (earthy natural)
    ColorCombo(
        R.string.combo_terrastta_sage,
        primary = Color(0xFFB85042),
        accent = Color(0xFFA7BEAE),
        surface = Color(0xFF2A1A16),
    ),
    // Peach + dusty blue (soft elegant)
    ColorCombo(
        R.string.combo_peach_dusty_blue,
        primary = Color(0xFFFFCBA4),
        accent = Color(0xFF6E8898),
        surface = Color(0xFF2A1E14),
    ),
    // Neon coral + space black (bold gaming)
    ColorCombo(
        R.string.combo_coral_black,
        primary = Color(0xFFFF6F61),
        accent = Color(0xFF121212),
        surface = Color(0xFF0A0A0A),
    ),
    // Ocean teal + sand beige (calm grounded)
    ColorCombo(
        R.string.combo_ocean_sand,
        primary = Color(0xFF009688),
        accent = Color(0xFFF5EBDD),
        surface = Color(0xFF0A1F1D),
    ),
    // Purple + gold (royal bold)
    ColorCombo(
        R.string.combo_royal_purple_gold,
        primary = Color(0xFF552583),
        accent = Color(0xFFFDB927),
        surface = Color(0xFF1A0E2E),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    navController: NavController,
) {
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (pureBlack, onPureBlackChangeRaw) = rememberPreference(PureBlackKey, defaultValue = false)
    val (_, onPureBlackMiniPlayerChange) = rememberPreference(
        PureBlackMiniPlayerKey,
        defaultValue = false
    )

    val onPureBlackChange: (Boolean) -> Unit = { enabled ->
        onPureBlackChangeRaw(enabled)
        onPureBlackMiniPlayerChange(enabled)
    }
    val (selectedThemeColorInt, onSelectedThemeColorChange) = rememberPreference(
        SelectedThemeColorKey,
        DefaultThemeColor.toArgb()
    )
    val (_, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, defaultValue = true)
    val (paletteMode, onPaletteModeChange) = rememberEnumPreference(PaletteModeKey, PaletteMode.HAND_PICKED)
    val (themeVariant, onThemeVariantChange) = rememberEnumPreference(ThemeVariantKey, com.soundsphere.music.constants.ThemeVariant.EARTHY)
    val (comboAccentColorInt, onComboAccentColorChange) = rememberPreference(ComboAccentColorKey, 0)
    val (wallpaperUri, onWallpaperUriChange) = rememberPreference(WallpaperUriKey, "")

    val selectedThemeColor = Color(selectedThemeColorInt)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Helper function to handle color selection with dynamic theme toggle
    val handleColorSelection: (Color) -> Unit = { color ->
        onSelectedThemeColorChange(color.toArgb())
        // Enable dynamic theme only when selecting the default/dynamic color
        // Disable it when selecting any other color
        val isDynamicColor = color == DefaultThemeColor
        onDynamicThemeChange(isDynamicColor)
    }

    if (isLandscape) {
        LandscapeThemeLayout(
            innerPadding = PaddingValues(0.dp),
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
            selectedThemeColor = selectedThemeColor,
            onSelectedThemeColorChange = handleColorSelection,
            paletteMode = paletteMode,
            onPaletteModeChange = onPaletteModeChange,
            themeVariant = themeVariant,
            onThemeVariantChange = onThemeVariantChange,
            comboAccentColorInt = comboAccentColorInt,
            onComboAccentColorChange = onComboAccentColorChange,
            wallpaperUri = wallpaperUri,
            onWallpaperUriChange = onWallpaperUriChange
        )
    } else {
        PortraitThemeLayout(
            innerPadding = PaddingValues(0.dp),
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
            selectedThemeColor = selectedThemeColor,
            onSelectedThemeColorChange = handleColorSelection,
            paletteMode = paletteMode,
            onPaletteModeChange = onPaletteModeChange,
            themeVariant = themeVariant,
            onThemeVariantChange = onThemeVariantChange,
            comboAccentColorInt = comboAccentColorInt,
            onComboAccentColorChange = onComboAccentColorChange,
            wallpaperUri = wallpaperUri,
            onWallpaperUriChange = onWallpaperUriChange
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.theme_colors)) },
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        }
    )
}

@Composable
fun PortraitThemeLayout(
    innerPadding: PaddingValues,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit,
    paletteMode: PaletteMode,
    onPaletteModeChange: (PaletteMode) -> Unit,
    themeVariant: ThemeVariant,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    comboAccentColorInt: Int,
    onComboAccentColorChange: (Int) -> Unit,
    wallpaperUri: String,
    onWallpaperUriChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .width(120.dp)
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            ThemeMockupPortrait(
                darkMode = darkMode,
                pureBlack = pureBlack,
                themeColor = selectedThemeColor,
                themeVariant = themeVariant,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        ThemeControls(
            darkMode = darkMode,
            onDarkModeChange = onDarkModeChange,
            pureBlack = pureBlack,
            onPureBlackChange = onPureBlackChange,
            selectedThemeColor = selectedThemeColor,
            onSelectedThemeColorChange = onSelectedThemeColorChange,
            paletteMode = paletteMode,
            onPaletteModeChange = onPaletteModeChange,
            themeVariant = themeVariant,
            onThemeVariantChange = onThemeVariantChange,
            comboAccentColorInt = comboAccentColorInt,
            onComboAccentColorChange = onComboAccentColorChange,
            wallpaperUri = wallpaperUri,
            onWallpaperUriChange = onWallpaperUriChange
        )

        Spacer(modifier = Modifier.height(120.dp))
    }
}

@Composable
fun LandscapeThemeLayout(
    innerPadding: PaddingValues,
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit,
    paletteMode: PaletteMode,
    onPaletteModeChange: (PaletteMode) -> Unit,
    themeVariant: ThemeVariant,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    comboAccentColorInt: Int,
    onComboAccentColorChange: (Int) -> Unit,
    wallpaperUri: String,
    onWallpaperUriChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .heightIn(max = 300.dp),
                contentAlignment = Alignment.Center
            ) {
                ThemeMockup(
                    darkMode = darkMode,
                    pureBlack = pureBlack,
                    themeColor = selectedThemeColor,
                    themeVariant = themeVariant,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            ThemeControls(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                pureBlack = pureBlack,
                onPureBlackChange = onPureBlackChange,
                selectedThemeColor = selectedThemeColor,
                onSelectedThemeColorChange = onSelectedThemeColorChange,
                paletteMode = paletteMode,
                onPaletteModeChange = onPaletteModeChange,
                themeVariant = themeVariant,
                onThemeVariantChange = onThemeVariantChange,
                comboAccentColorInt = comboAccentColorInt,
                onComboAccentColorChange = onComboAccentColorChange,
                wallpaperUri = wallpaperUri,
                onWallpaperUriChange = onWallpaperUriChange
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun ThemeControls(
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
    pureBlack: Boolean,
    onPureBlackChange: (Boolean) -> Unit,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit,
    paletteMode: PaletteMode,
    onPaletteModeChange: (PaletteMode) -> Unit,
    themeVariant: ThemeVariant,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    comboAccentColorInt: Int,
    onComboAccentColorChange: (Int) -> Unit,
    wallpaperUri: String,
    onWallpaperUriChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Look section (Material U variant)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.theme_look),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemeVariantChip(
                        label = stringResource(R.string.look_earthy),
                        isSelected = themeVariant == ThemeVariant.EARTHY,
                        onClick = { onThemeVariantChange(ThemeVariant.EARTHY) },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeVariantChip(
                        label = stringResource(R.string.look_material_u),
                        isSelected = themeVariant == ThemeVariant.MATERIAL_U,
                        onClick = { onThemeVariantChange(ThemeVariant.MATERIAL_U) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.theme_mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // System mode (AUTO)
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.AUTO,
                        targetPureBlack = pureBlack,
                        onClick = {
                            onDarkModeChange(DarkMode.AUTO)
                        },
                        showIcon = true
                    )
                    
                    // Vertical divider to separate System from manual modes
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    
                    // Manual modes (Light, Dark, Pure Black)
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.OFF,
                        targetPureBlack = false,
                        onClick = {
                            onDarkModeChange(DarkMode.OFF)
                            onPureBlackChange(false)
                        },
                        showIcon = false
                    )
                    
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.ON,
                        targetPureBlack = false,
                        onClick = {
                            onDarkModeChange(DarkMode.ON)
                            onPureBlackChange(false)
                        },
                        showIcon = false
                    )
                    
                    ModeCircle(
                        darkMode = darkMode,
                        pureBlack = pureBlack,
                        targetMode = DarkMode.ON,
                        targetPureBlack = true,
                        onClick = {
                            onDarkModeChange(DarkMode.ON)
                            onPureBlackChange(true)
                        },
                        showIcon = false
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.color_palette),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // 3-way mode switch
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        // Hand-picked chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (paletteMode == PaletteMode.HAND_PICKED)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable { onPaletteModeChange(PaletteMode.HAND_PICKED) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.palette_hand_picked),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (paletteMode == PaletteMode.HAND_PICKED)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Combos chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (paletteMode == PaletteMode.PRE_SELECTED_COMBOS)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable { onPaletteModeChange(PaletteMode.PRE_SELECTED_COMBOS) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.palette_pre_selected),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (paletteMode == PaletteMode.PRE_SELECTED_COMBOS)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Auto-generated chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (paletteMode == PaletteMode.AUTO_GENERATED)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable { onPaletteModeChange(PaletteMode.AUTO_GENERATED) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.palette_auto_generated),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (paletteMode == PaletteMode.AUTO_GENERATED)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                when (paletteMode) {
                    PaletteMode.HAND_PICKED -> {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(PaletteColors) { palette ->
                                val isDynamicPalette = palette.seedColor == Color.Transparent
                                val isSelected = if (isDynamicPalette) {
                                    selectedThemeColor == DefaultThemeColor
                                } else {
                                    selectedThemeColor == palette.seedColor
                                }
                                
                                PaletteItem(
                                    palette = palette,
                                    isSelected = isSelected,
                                    onClick = { 
                                        val colorToSave = if (isDynamicPalette) DefaultThemeColor else palette.seedColor
                                        onSelectedThemeColorChange(colorToSave) 
                                    }
                                )
                            }
                        }
                    }
                    
                    PaletteMode.PRE_SELECTED_COMBOS -> {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(ColorCombos) { combo ->
                                ComboItem(
                                    combo = combo,
                                    isSelected = selectedThemeColor == combo.primary,
                                    onClick = { 
                                        onSelectedThemeColorChange(combo.primary)
                                        onComboAccentColorChange(combo.accent.toArgb())
                                    }
                                )
                            }
                        }
                    }
                    
                    PaletteMode.AUTO_GENERATED -> {
                        // Wallpaper picker button + description
                        val context = LocalContext.current
                        val currentWallpaperUri = wallpaperUri
                        
                        val wallpaperLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetContent()
                        ) { uri: Uri? ->
                            uri?.let {
                                // Copy the picked image into app-private storage so the
                                // stored reference outlives the picker's temporary URI
                                // grant (content:// URIs become unreadable after the
                                // process is killed). Persist the file:// path instead.
                                val persistedPath = copyWallpaperToInternalStorage(context, it)
                                if (persistedPath != null) {
                                    onWallpaperUriChange(persistedPath)
                                }

                                // Extract color from image
                                try {
                                    val inputStream = context.contentResolver.openInputStream(it)
                                    val bitmap = BitmapFactory.decodeStream(inputStream)
                                    inputStream?.close()
                                    bitmap?.let { bmp ->
                                        val color = bmp.extractThemeColor()
                                        onSelectedThemeColorChange(color)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Wallpaper preview + picker
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { wallpaperLauncher.launch("image/*") },
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                if (currentWallpaperUri.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(Uri.parse(currentWallpaperUri))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(MaterialTheme.shapes.large),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.insert_photo),
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = stringResource(R.string.combo_from_wallpaper),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Text(
                                text = stringResource(R.string.auto_generated_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Use the picked wallpaper as the whole-app background
                            if (currentWallpaperUri.isNotEmpty()) {
                                val (wallpaperBackground, onWallpaperBackgroundChange) =
                                    rememberPreference(WallpaperBackgroundKey, defaultValue = false)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = stringResource(R.string.wallpaper_background),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    androidx.compose.material3.Switch(
                                        checked = wallpaperBackground,
                                        onCheckedChange = onWallpaperBackgroundChange,
                                        thumbContent = {
                                            Icon(
                                                painter = painterResource(
                                                    id = if (wallpaperBackground) R.drawable.check else R.drawable.close
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeCircle(
    darkMode: DarkMode,
    pureBlack: Boolean,
    targetMode: DarkMode,
    targetPureBlack: Boolean,
    showIcon: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isSelected = darkMode == targetMode && pureBlack == targetPureBlack
    
    val effectiveDark = when (targetMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }
    
    // Use actual system colors for AUTO mode on Android 12+
    val modeColorScheme = if (targetMode == DarkMode.AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = DefaultThemeColor,
            isDark = effectiveDark,
            style = PaletteStyle.TonalSpot
        )
    }
    
    val fillColor = when {
        targetPureBlack -> Color.Black
        effectiveDark -> modeColorScheme.surface
        else -> modeColorScheme.surface
    }
    
    // Animated border width
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "borderWidth"
    )
    
    // Animated scale for the entire circle
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    
    val contentDesc = when {
        targetPureBlack -> stringResource(R.string.cd_pure_black_mode)
        targetMode == DarkMode.OFF -> stringResource(R.string.cd_light_mode)
        targetMode == DarkMode.ON -> stringResource(R.string.cd_dark_mode)
        else -> stringResource(R.string.cd_system_mode)
    }
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(fillColor)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(
                        width = borderWidth,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = contentDesc
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            showIcon -> {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = null,
                    tint = modeColorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            isSelected -> {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                        initialScale = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                    exit = fadeOut(animationSpec = tween(150)) + scaleOut(
                        targetScale = 0.3f,
                        animationSpec = tween(150)
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeVariantChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PaletteItem(
    palette: ThemePalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    
    val colorScheme = rememberDynamicColorScheme(
        seedColor = palette.seedColor,
        isDark = isSystemDark,
        style = PaletteStyle.TonalSpot
    )
    
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 48.dp * 0.25f else 24.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cornerRadius"
    )
    
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "borderWidth"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    
    val paletteName = stringResource(palette.nameRes)
    val contentDesc = stringResource(R.string.cd_palette_item, paletteName)
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(
                        width = borderWidth,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = contentDesc
            }
    ) {
        if (palette.seedColor == Color.Transparent) {
            // Draw Dynamic/System icon using Material Design icon
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.palette),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                drawRect(
                    color = colorScheme.onPrimary,
                    topLeft = Offset(0f, 0f),
                    size = Size(width, height / 2)
                )
                
                drawRect(
                    color = colorScheme.secondary,
                    topLeft = Offset(0f, height / 2),
                    size = Size(width / 2, height / 2)
                )
                
                drawRect(
                    color = colorScheme.tertiary,
                    topLeft = Offset(width / 2, height / 2),
                    size = Size(width / 2, height / 2)
                )
            }
        }
    }
}

@Composable
fun ComboItem(
    combo: ColorCombo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 48.dp * 0.25f else 24.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cornerRadius"
    )
    
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "borderWidth"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    
    val comboName = stringResource(combo.nameRes)
    val contentDesc = stringResource(R.string.cd_palette_item, comboName)
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(
                        width = borderWidth,
                        color = MaterialTheme.colorScheme.inversePrimary,
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .semantics {
                contentDescription = contentDesc
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Primary color (top half)
            drawRect(
                color = combo.primary,
                topLeft = Offset(0f, 0f),
                size = Size(width, height / 2)
            )
            
            // Accent color (bottom half)
            drawRect(
                color = combo.accent,
                topLeft = Offset(0f, height / 2),
                size = Size(width, height / 2)
            )
        }
    }
}

@Composable
fun ThemeMockup(
    darkMode: DarkMode,
    pureBlack: Boolean,
    themeColor: Color,
    themeVariant: ThemeVariant = ThemeVariant.EARTHY,
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }

    SoundsphereTheme(
        darkTheme = useDark,
        pureBlack = pureBlack,
        themeColor = themeColor,
        themeVariant = themeVariant,
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(9f / 18f),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(6.dp))
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(6.dp))
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeMockupPortrait(
    darkMode: DarkMode,
    pureBlack: Boolean,
    themeColor: Color,
    themeVariant: ThemeVariant = ThemeVariant.EARTHY,
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }

    SoundsphereTheme(
        darkTheme = useDark,
        pureBlack = pureBlack,
        themeColor = themeColor,
        themeVariant = themeVariant,
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header (20% of height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        )
                    }
                }

                // Main Content (60% of height)
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp))
                        )
                    }
                }

                // FAB Area (20% of height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .padding(6.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Copies the picked wallpaper into app-private storage so the persisted
 * reference survives the picker's temporary content URI grant (which expires
 * once the app process is killed). The file gets a unique, timestamped name
 * and any previously copied wallpapers are deleted, so a new pick produces a
 * new stored URI — which also forces Coil to re-decode instead of serving the
 * old bitmap from its cache (the cache key is the URI string). Returns the
 * new file:// path, or null.
 */
private fun copyWallpaperToInternalStorage(
    context: android.content.Context,
    uri: Uri,
): String? = runCatching {
    // Remove any previously copied wallpapers so app storage doesn't accumulate picks
    context.filesDir.listFiles()
        ?.filter { it.name.startsWith("wallpaper_") }
        ?.forEach { it.delete() }
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    val outputFile = java.io.File(context.filesDir, "wallpaper_${System.currentTimeMillis()}.jpg")
    inputStream.use { input ->
        outputFile.outputStream().use { output -> input.copyTo(output) }
    }
    Uri.fromFile(outputFile).toString()
}.getOrNull()
