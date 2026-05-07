package com.recapmaker.app.util

import com.recapmaker.app.data.model.PricingTier
import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {

    private val tiers = listOf(
        PricingTier(max_seconds = 60, cost = 10),
        PricingTier(max_seconds = 300, cost = 50),
        PricingTier(max_seconds = 600, cost = 100)
    )

    @Test
    fun `getCostForDuration - within first tier`() {
        assertEquals(10, getCostForDuration(30, tiers))
    }

    @Test
    fun `getCostForDuration - exactly at first tier boundary`() {
        assertEquals(10, getCostForDuration(60, tiers))
    }

    @Test
    fun `getCostForDuration - just above first tier boundary`() {
        assertEquals(50, getCostForDuration(61, tiers))
    }

    @Test
    fun `getCostForDuration - within middle tier`() {
        assertEquals(50, getCostForDuration(150, tiers))
    }

    @Test
    fun `getCostForDuration - exactly at middle tier boundary`() {
        assertEquals(50, getCostForDuration(300, tiers))
    }

    @Test
    fun `getCostForDuration - within last tier`() {
        assertEquals(100, getCostForDuration(500, tiers))
    }

    @Test
    fun `getCostForDuration - exactly at last tier boundary`() {
        assertEquals(100, getCostForDuration(600, tiers))
    }

    @Test
    fun `getCostForDuration - exceeding all tiers`() {
        assertEquals(-1, getCostForDuration(601, tiers))
    }

    @Test
    fun `getCostForDuration - empty tiers list`() {
        assertEquals(-1, getCostForDuration(30, emptyList()))
    }

    @Test
    fun `getCostForDuration - unsorted tiers list`() {
        val unsortedTiers = listOf(
            PricingTier(max_seconds = 600, cost = 100),
            PricingTier(max_seconds = 60, cost = 10),
            PricingTier(max_seconds = 300, cost = 50)
        )
        assertEquals(10, getCostForDuration(30, unsortedTiers))
        assertEquals(10, getCostForDuration(60, unsortedTiers))
        assertEquals(50, getCostForDuration(61, unsortedTiers))
        assertEquals(100, getCostForDuration(600, unsortedTiers))
        assertEquals(-1, getCostForDuration(601, unsortedTiers))
    }

    @Test
    fun `getCostForDuration - zero duration`() {
        assertEquals(10, getCostForDuration(0, tiers))
    }

    @Test
    fun `getCostForDuration - negative duration`() {
        assertEquals(10, getCostForDuration(-10, tiers))
    }

    @Test
    fun `formatDuration - seconds only`() {
        assertEquals("0:45", formatDuration(45))
    }

    @Test
    fun `formatDuration - minutes and seconds`() {
        assertEquals("1:05", formatDuration(65))
        assertEquals("10:00", formatDuration(600))
    }

    @Test
    fun `formatDuration - zero`() {
        assertEquals("0:00", formatDuration(0))
    }

    @Test
    fun `formatFileSize - B`() {
        assertEquals("500 B", formatFileSize(500))
    }

    @Test
    fun `formatFileSize - KB`() {
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("500.0 KB", formatFileSize(512000))
    }

    @Test
    fun `formatFileSize - MB`() {
        assertEquals("1.0 MB", formatFileSize(1024 * 1024))
        assertEquals("1.5 MB", formatFileSize((1.5 * 1024 * 1024).toLong()))
    }
}
