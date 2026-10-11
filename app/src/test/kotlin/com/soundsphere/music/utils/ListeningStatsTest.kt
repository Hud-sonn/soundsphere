package com.soundsphere.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ListeningStatsTest {
    private val today = LocalDate.of(2026, 9, 20)

    @Test
    fun `three day streak ending today`() {
        assertEquals(
            3,
            currentStreak(
                listOf(today, today.minusDays(1), today.minusDays(2)),
                today,
            ),
        )
    }

    @Test
    fun `streak survives a quiet today`() {
        assertEquals(
            2,
            currentStreak(
                listOf(today.minusDays(1), today.minusDays(2)),
                today,
            ),
        )
    }

    @Test
    fun `gap resets the streak`() {
        assertEquals(
            1,
            currentStreak(
                listOf(today, today.minusDays(3), today.minusDays(4)),
                today,
            ),
        )
    }

    @Test
    fun `empty history is zero`() {
        assertEquals(0, currentStreak(emptyList(), today))
    }

    @Test
    fun `single play yesterday counts one`() {
        assertEquals(1, currentStreak(listOf(today.minusDays(1)), today))
    }

    @Test
    fun `duplicate same-day plays count once`() {
        assertEquals(
            2,
            currentStreak(
                listOf(today, today, today.minusDays(1), today.minusDays(1)),
                today,
            ),
        )
    }
}
