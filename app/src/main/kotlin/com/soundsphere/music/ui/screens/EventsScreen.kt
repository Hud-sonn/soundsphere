/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.R
import com.soundsphere.music.api.FeedEvent
import com.soundsphere.music.ui.component.ErrorRetryPlaceholder
import com.soundsphere.music.ui.component.IconButton
import com.soundsphere.music.ui.component.NavigationTitle
import com.soundsphere.music.ui.component.shimmer.ShimmerHost
import com.soundsphere.music.ui.component.shimmer.TextPlaceholder
import com.soundsphere.music.ui.utils.backToMain
import com.soundsphere.music.viewmodels.EventsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Month + day bucket derived from an ISO start time, falling back to raw text. */
private data class EventDate(
    val month: String,
    val day: String,
    val full: String,
)

private fun FeedEvent.eventDate(): EventDate {
    val raw = startTime?.take(10).orEmpty()
    fun fallback() = EventDate(
        month = raw.take(7),
        day = "",
        full = raw,
    )
    if (raw.isBlank()) return EventDate("", "", "")
    return try {
        val instant = try {
            Instant.parse(startTime)
        } catch (_: Exception) {
            LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant()
        }
        val zoned = instant.atZone(ZoneId.systemDefault())
        EventDate(
            month = zoned.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())),
            day = zoned.format(DateTimeFormatter.ofPattern("d", Locale.getDefault())),
            full = zoned.format(
                DateTimeFormatter.ofPattern("EEE • MMM d, yyyy", Locale.getDefault()),
            ),
        )
    } catch (_: Exception) {
        fallback()
    }
}

private fun FeedEvent.locationLine(): String =
    listOfNotNull(venue, city, country?.takeIf { it.isNotBlank() })
        .filter { it.isNotBlank() }
        .joinToString(", ")

@Composable
private fun openTicket(url: String?): () -> Unit {
    val context = LocalContext.current
    return {
        url?.let {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
        }
    }
}

/** Imageless fallback: venue initial on a tonal tile. */
@Composable
private fun EventArtworkFallback(
    event: FeedEvent,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (event.venue?.firstOrNull() ?: event.title.firstOrNull() ?: "?").toString(),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Hero card for the nearest followed-artist event (adapted from the mock showcase). */
@Composable
private fun EventHeroCard(event: FeedEvent) {
    val date = event.eventDate()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column {
            if (event.image != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(event.image)
                        .crossfade(true)
                        .build(),
                    contentDescription = event.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                event.artistName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val location = event.locationLine()
                if (location.isNotBlank()) {
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (date.full.isNotBlank()) {
                    Text(
                        text = date.full,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (event.ticketUrl != null && !event.soldOut) {
                        Button(onClick = openTicket(event.ticketUrl)) {
                            Text(stringResource(R.string.book_tickets))
                        }
                    }
                    if (event.soldOut) {
                        Text(
                            text = stringResource(R.string.sold_out),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Expandable date card (adapted from the mock tour-date cards, real fields only). */
@Composable
private fun EventCard(event: FeedEvent) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val date = event.eventDate()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp),
                ) {
                    if (date.month.isNotBlank()) {
                        Text(
                            text = date.month.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (date.day.isNotBlank()) {
                        Text(
                            text = date.day,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                if (event.image != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(event.image)
                            .crossfade(true)
                            .build(),
                        contentDescription = event.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    EventArtworkFallback(
                        event = event,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    event.artistName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val location = event.locationLine()
                    if (location.isNotBlank()) {
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (event.soldOut) {
                    Text(
                        text = stringResource(R.string.sold_out),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (date.full.isNotBlank()) {
                        Text(
                            text = date.full,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    event.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (event.ticketUrl != null && !event.soldOut) {
                            Button(onClick = openTicket(event.ticketUrl)) {
                                Text(stringResource(R.string.book_tickets))
                            }
                        }
                        event.url?.let {
                            Text(
                                text = stringResource(R.string.event_details),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = openTicket(it)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    navController: NavController,
    viewModel: EventsViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val followed by viewModel.followedEvents.collectAsStateWithLifecycle()
    val discover by viewModel.discoverEvents.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    if (error != null && followed.isEmpty() && discover.isEmpty() && !isLoading) {
        ErrorRetryPlaceholder(
            message = stringResource(R.string.error_loading_events),
            onRetry = viewModel::refresh,
        )
    } else {
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (isLoading && followed.isEmpty() && discover.isEmpty()) {
                item(key = "events_loading") {
                    ShimmerHost {
                        TextPlaceholder(
                            height = 200.dp,
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                        )
                        repeat(3) {
                            TextPlaceholder(
                                height = 80.dp,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (followed.isNotEmpty()) {
                item(key = "events_hero") {
                    EventHeroCard(event = followed.first())
                }
            }

            if (followed.size > 1) {
                item(key = "events_followed_title") {
                    NavigationTitle(title = stringResource(R.string.from_artists_you_follow))
                }
                items(followed.drop(1), key = { "${it.source}:${it.sourceId}" }) { event ->
                    EventCard(event = event)
                }
            }

            if (discover.isNotEmpty()) {
                item(key = "events_discover_title") {
                    NavigationTitle(title = stringResource(R.string.discover_events))
                }
                items(discover, key = { "discover_${it.source}:${it.sourceId}" }) { event ->
                    EventCard(event = event)
                }
            }

            if (!isLoading && followed.isEmpty() && discover.isEmpty()) {
                item(key = "events_empty") {
                    Text(
                        text = stringResource(R.string.no_upcoming_events),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            item(key = "events_bottom_space") {
                // Spacer above the mini player
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.height(
                        WindowInsets.systemBars.only(WindowInsetsSides.Bottom)
                            .asPaddingValues().calculateBottomPadding(),
                    ),
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.events_live)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
