/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.utils

import java.time.LocalDate

/**
 * Pure listening-streak math (no Android, no DB — fully unit-testable).
 *
 * A "streak" is consecutive calendar days with at least one play, counted
 * back from today. If nothing played yet today, counting starts from
 * yesterday so an active streak doesn't read 0 in the morning.
 */
fun currentStreak(
    playDatesDesc: List<LocalDate>,
    today: LocalDate = LocalDate.now(),
): Int {
    if (playDatesDesc.isEmpty()) return 0
    val played = playDatesDesc.toSet()
    var cursor = if (today in played) today else today.minusDays(1)
    var streak = 0
    while (cursor in played) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}
