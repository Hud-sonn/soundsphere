package com.soundsphere.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartShuffleTest {
    @Test
    fun `empty stays empty`() {
        val out: List<Int> = smartShuffle(emptyList(), weightOf = { it.toDouble() })
        assertEquals(emptyList<Int>(), out)
    }

    @Test
    fun `single unchanged`() {
        val out: List<Int> = smartShuffle(listOf(1), weightOf = { it.toDouble() })
        assertEquals(listOf(1), out)
    }

    @Test
    fun `deterministic with same seed`() {
        val items = listOf(1, 2, 3, 4, 5)
        val a = smartShuffle(items, { it.toDouble() }, seed = 42L)
        val b = smartShuffle(items, { it.toDouble() }, seed = 42L)
        assertEquals(a, b)
    }

    @Test
    fun `all items preserved`() {
        val items = listOf(1, 2, 3, 4)
        val shuffled = smartShuffle(items, { it.toDouble() }, seed = 7L)
        assertEquals(items.sorted(), shuffled.sorted())
    }

    @Test
    fun `zero weight still included`() {
        val items = listOf(10, 20)
        val shuffled = smartShuffle(items, { 0.0 }, seed = 1L)
        assertEquals(2, shuffled.size)
        assertTrue(shuffled.containsAll(items))
    }
}
