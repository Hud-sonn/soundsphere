/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.utils

import com.soundsphere.music.db.entities.Song

/**
 * Library health checks — pure functions over the in-library song list.
 * Read-only diagnostics; auto-fix actions live in the ViewModel layer.
 */

/** Songs sharing a normalized title+artist key (case/space-insensitive). */
fun findDuplicateSongs(songs: List<Song>): List<List<Song>> {
    fun key(s: Song): String {
        val title = s.song.title.trim().lowercase()
        val artist = s.artists.joinToString(",") { it.name.trim().lowercase() }
        return "$title||$artist"
    }
    return songs
        .groupBy(::key)
        .values
        .filter { it.size > 1 }
        .sortedByDescending { it.size }
}

/** In-library songs with no artwork URL at all. */
fun findSongsMissingArt(songs: List<Song>): List<Song> =
    songs.filter { it.song.thumbnailUrl.isNullOrBlank() }

/** Uploaded songs whose local file info is gone (dead uploads). */
fun findDeadUploads(songs: List<Song>): List<Song> =
    songs.filter {
        it.song.isUploaded && it.song.thumbnailUrl.isNullOrBlank() && it.song.duration <= 0
    }

data class LibraryHealthReport(
    val duplicateGroups: List<List<Song>> = emptyList(),
    val missingArt: List<Song> = emptyList(),
    val deadUploads: List<Song> = emptyList(),
) {
    val duplicateCount: Int get() = duplicateGroups.sumOf { it.size - 1 }
    val isClean: Boolean get() = duplicateGroups.isEmpty() && missingArt.isEmpty() && deadUploads.isEmpty()
}

fun buildHealthReport(songs: List<Song>): LibraryHealthReport =
    LibraryHealthReport(
        duplicateGroups = findDuplicateSongs(songs),
        missingArt = findSongsMissingArt(songs),
        deadUploads = findDeadUploads(songs),
    )
