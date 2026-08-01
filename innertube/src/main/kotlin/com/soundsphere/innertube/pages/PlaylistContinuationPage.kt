package com.soundsphere.innertube.pages

import com.soundsphere.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
