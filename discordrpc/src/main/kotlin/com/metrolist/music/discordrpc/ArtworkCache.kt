package com.metrolist.music.discordrpc

import java.util.concurrent.ConcurrentHashMap

internal object ArtworkCache {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun getOrFetch(key: String, fetch: suspend () -> String?): String? {
        val cached = cache[key]
        if (cached != null) return cached
        val result = fetch()
        if (result != null) {
            cache[key] = result
            return result
        }
        return null
    }

    fun clear() {
        cache.clear()
    }
}
