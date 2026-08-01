package com.soundsphere.innertube.pages

import com.soundsphere.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
