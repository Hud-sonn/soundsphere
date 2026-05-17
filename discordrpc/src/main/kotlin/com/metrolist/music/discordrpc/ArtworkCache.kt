package com.metrolist.music.discordrpc

import java.util.concurrent.ConcurrentHashMap

internal object ArtworkCache {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun getOrFetch(key: String, fetch: suspend () -> String?): String? {
        return cache[key] ?: fetch()?.also { cache[key] = it }
    }
}
