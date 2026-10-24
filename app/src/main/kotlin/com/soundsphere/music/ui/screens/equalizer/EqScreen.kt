package com.soundsphere.music.ui.screens.equalizer

import android.annotation.SuppressLint
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundsphere.music.LocalNavController
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.LocalSyncRepository
import com.soundsphere.music.R
import com.soundsphere.music.data.PAYMENTS_ENABLED
import com.soundsphere.music.eq.data.OutputRoute
import com.soundsphere.music.eq.data.SavedEQProfile
import timber.log.Timber

/**
 * EQ Screen - Manage and select EQ profiles
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun EqScreen(
    viewModel: EQViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showError by remember { mutableStateOf<String?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }

    // File picker for custom EQ import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver

                // Extract file name from URI
                var fileName = "custom_eq.txt"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex >= 0) {
                            val name = cursor.getString(displayNameIndex)
                            if (!name.isNullOrBlank()) {
                                fileName = name
                            }
                        }
                    }
                }

                val inputStream = contentResolver.openInputStream(uri)

                if (inputStream != null) {
                    viewModel.importCustomProfile(
                        fileName = fileName,
                        inputStream = inputStream,
                        onSuccess = {
                            Timber.d("Custom EQ profile imported successfully: $fileName")
                        },
                        onError = { error ->
                            Timber.d("Error: Unable to import Custom EQ profile: $fileName")
                            showError = context.getString(R.string.import_error_title) + ": " + error.message
                        })
                } else {
                    showError = context.getString(R.string.error_file_read)
                }
            } catch (e: Exception) {
                showError = context.getString(R.string.error_file_open, e.message)
            }
        }
    }

    val activeProfile = state.profiles.find { it.id == state.activeProfileId }

    EqScreenContent(
        profiles = state.profiles,
        activeProfileId = state.activeProfileId,
        activeProfile = activeProfile,
        routeMap = state.routeMap,
        onProfileSelected = { viewModel.selectProfile(it) },
        onRouteProfileSelected = { route, profileId -> viewModel.setRouteProfile(route, profileId) },
        onNavigateBack = { navController.navigateUp() },
        showAddMenu = showAddMenu,
        onAddClicked = { showAddMenu = true },
        onAddMenuDismissed = { showAddMenu = false },
        onWizardClicked = {
            showAddMenu = false
            navController.navigate("eq_wizard")
        },
        onImportClicked = {
            showAddMenu = false
            filePickerLauncher.launch("text/plain")
        },
        onDeleteProfile = { viewModel.deleteProfile(it) }
    )

    // Error dialog
    if (showError != null) {
        AlertDialog(
            onDismissRequest = { showError = null },
            title = {
                Text(stringResource(R.string.import_error_title))
            },
            text = {
                Text(showError ?: "")
            },
            confirmButton = {
                TextButton(onClick = { showError = null }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    // Error dialog for apply failure
    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = {
                Text(stringResource(R.string.error_title))
            },
            text = {
                Text(stringResource(R.string.error_eq_apply_failed, state.error ?: ""))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqScreenContent(
    profiles: List<SavedEQProfile>,
    activeProfileId: String?,
    activeProfile: SavedEQProfile?,
    routeMap: Map<String, String>,
    onProfileSelected: (String?) -> Unit,
    onRouteProfileSelected: (OutputRoute, String?) -> Unit,
    onNavigateBack: () -> Unit,
    showAddMenu: Boolean,
    onAddClicked: () -> Unit,
    onAddMenuDismissed: () -> Unit,
    onWizardClicked: () -> Unit,
    onImportClicked: () -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.equalizer_header)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = onAddClicked) {
                            Icon(
                                painter = painterResource(R.drawable.add),
                                contentDescription = stringResource(R.string.import_profile)
                            )
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = onAddMenuDismissed
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.eq_wizard)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.discover_tune),
                                        contentDescription = null
                                    )
                                },
                                onClick = onWizardClicked
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_from_file)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.upload),
                                        contentDescription = null
                                    )
                                },
                                onClick = onImportClicked
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        // Profile list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = LocalPlayerAwareWindowInsets.current
                .asPaddingValues().calculateBottomPadding())
        ) {
            // Frequency response graph
            item {
                EqFrequencyResponseGraph(
                    bands = activeProfile?.bands ?: emptyList(),
                    preamp = activeProfile?.preamp ?: 0.0
                )
            }

            // "No Equalization" option (always first)
            item {
                NoEqualizationItem(
                    isSelected = activeProfileId == null,
                    onSelected = { onProfileSelected(null) }
                )
            }

            // Per-output auto-switch: pick a profile per route (or keep global)
            item {
                RouteEqSection(
                    profiles = profiles,
                    routeMap = routeMap,
                    onRouteProfileSelected = onRouteProfileSelected,
                )
            }

            // Custom profiles only
            val customProfiles = profiles.filter { it.isCustom }

            if (customProfiles.isNotEmpty()) {
                items(customProfiles) { profile ->
                    EQProfileItem(
                        profile = profile,
                        isSelected = activeProfileId == profile.id,
                        onSelected = { onProfileSelected(profile.id) },
                        onDelete = { onDeleteProfile(profile.id) }
                    )
                }
            }

            // Empty state
            if (customProfiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.equalizer),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.no_profiles),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onAddClicked) {
                                Text(stringResource(R.string.import_profile))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- HELPER COMPOSABLES ---

@Composable
private fun NoEqualizationItem(
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                stringResource(R.string.eq_disabled),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        leadingContent = {
            RadioButton(
                selected = isSelected,
                onClick = onSelected
            )
        },
        modifier = Modifier
            .clickable(onClick = onSelected)
            .padding(horizontal = 8.dp) // align with design
    )
}

@Composable
private fun EQProfileItem(
    profile: SavedEQProfile,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                text = profile.deviceModel,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        supportingContent = {
            Text(
                pluralStringResource(
                    id = R.plurals.band_count,
                    count = profile.bands.size,
                    profile.bands.size
                )
            )
        },
        leadingContent = {
            RadioButton(
                selected = isSelected,
                onClick = onSelected
            )
        },
        trailingContent = {
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = stringResource(R.string.delete_profile_desc),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        modifier = Modifier
            .clickable(onClick = onSelected)
            .padding(horizontal = 8.dp)
    )

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_profile_desc)) },
            text = {
                Text(
                    stringResource(R.string.delete_profile_confirmation, profile.name)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

private val ROUTE_ROWS =
    listOf(
        OutputRoute.BLUETOOTH to R.string.eq_route_bluetooth,
        OutputRoute.SPEAKER to R.string.eq_route_speaker,
        OutputRoute.WIRED to R.string.eq_route_wired,
    )

/**
 * Per-output auto-switch rows: pick which profile activates when a given
 * output route connects, or keep the globally active profile ("None" =
 * unmapped). MusicService applies the mapping on device connect/disconnect.
 */
@Composable
private fun RouteEqSection(
    profiles: List<SavedEQProfile>,
    routeMap: Map<String, String>,
    onRouteProfileSelected: (OutputRoute, String?) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(
            text = stringResource(R.string.eq_route_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val isPremium by LocalSyncRepository.current.isPremium.collectAsStateWithLifecycle()
        if (!PAYMENTS_ENABLED || isPremium) {
            ROUTE_ROWS.forEach { (route, labelRes) ->
                var expanded by remember { mutableStateOf(false) }
                val selectedId = routeMap[route.name]
                val selectedName =
                    profiles.firstOrNull { it.id == selectedId }?.deviceModel
                        ?: stringResource(R.string.eq_route_none)
                ListItem(
                    headlineContent = { Text(stringResource(labelRes)) },
                    supportingContent = { Text(selectedName) },
                    modifier = Modifier.clickable { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.eq_route_none)) },
                        onClick = {
                            onRouteProfileSelected(route, null)
                            expanded = false
                        },
                    )
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.deviceModel) },
                            onClick = {
                                onRouteProfileSelected(route, profile.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.eq_route_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            val routeNavController = LocalNavController.current
            ListItem(
                headlineContent = { Text(stringResource(R.string.premium_feature)) },
                supportingContent = { Text(stringResource(R.string.eq_route_pro_desc)) },
                leadingContent = {
                    Icon(
                        painter = painterResource(R.drawable.lock),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
                trailingContent = {
                    Button(onClick = { routeNavController.navigate("payment") }) {
                        Text(stringResource(R.string.premium_unlock))
                    }
                },
            )
        }
    }
}
