/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.utils

import com.soundsphere.music.db.entities.Song
import kotlin.math.ln
import kotlin.random.Random

/**
 * Taste-weighted shuffle for Smart Shuffle (premium). Songs you actually
 * listen to surface, songs you skip sink — without ever being deterministic.
 *
 * Weight = ln(1 + totalPlayTime/60000) / (1 + skipCount * 0.7). The log
 * keeps marathon favorites from dominating; each skip is ~40% penalty.
 * Final order is sampled without replacement using those weights, seeded
 * by [seed] so the same queue shuffles the same way within a session.
 *
 * Pure function — no Android, no DB — fully unit-testable.
 */
fun <T> smartShuffle(
    items: List<T>,
    weightOf: (T) -> Double,
    seed: Long = System.currentTimeMillis(),
): List<T> {
    if (items.size <= 1) return items.toList()
    val rnd = Random(seed)
    val mutable = items.toMutableList()
    val weights = mutable.map { weightOf(it).coerceAtLeast(0.05) }.toMutableList()
    val out = mutableListOf<T>()
    while (mutable.isNotEmpty()) {
        val total = weights.sum()
        var r = rnd.nextDouble() * total
        var idx = 0
        while (idx < weights.size - 1 && r > weights[idx]) {
            r -= weights[idx]
            idx++
        }
        out.add(mutable.removeAt(idx))
        weights.removeAt(idx)
    }
    return out
}

fun songWeight(song: Song): Double {
    // song.song is SongEntity
    val minutes = song.song.totalPlayTime / 60000.0
    return ln(1.0 + minutes) / (1.0 + song.song.skipCount * 0.7)
}
