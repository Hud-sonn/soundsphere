package com.soundsphere.innertube.models.body

import com.soundsphere.innertube.models.Context
import com.soundsphere.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
