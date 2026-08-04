package com.recapmaker.app.media

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class FFmpegProcessorTest {

    @Test
    fun `qualityPreset maps fast to q:v 3 and 3000 kbps`() {
        val opts = FFmpegProcessor.ProcessOptions(outputQuality = "fast")
        val (qVal, kbps) = FFmpegProcessor.qualityPreset(opts)
        assertEquals(3, qVal)
        assertEquals(3000, kbps)
    }

    @Test
    fun `qualityPreset maps balanced to q:v 1 and 5000 kbps`() {
        val opts = FFmpegProcessor.ProcessOptions(outputQuality = "balanced")
        val (qVal, kbps) = FFmpegProcessor.qualityPreset(opts)
        assertEquals(1, qVal)
        assertEquals(5000, kbps)
    }

    @Test
    fun `qualityPreset maps quality to q:v 1 and 8000 kbps`() {
        val opts = FFmpegProcessor.ProcessOptions(outputQuality = "quality")
        val (qVal, kbps) = FFmpegProcessor.qualityPreset(opts)
        assertEquals(1, qVal)
        assertEquals(8000, kbps)
    }

    @Test
    fun `qualityPreset maps max to q:v 0 and 15000 kbps`() {
        val opts = FFmpegProcessor.ProcessOptions(outputQuality = "max")
        val (qVal, kbps) = FFmpegProcessor.qualityPreset(opts)
        assertEquals(0, qVal)
        assertEquals(15000, kbps)
    }

    @Test
    fun `qualityPreset maps unknown to balanced defaults`() {
        val opts = FFmpegProcessor.ProcessOptions(outputQuality = "unknown")
        val (qVal, kbps) = FFmpegProcessor.qualityPreset(opts)
        assertEquals(1, qVal)
        assertEquals(5000, kbps)
    }

    @Test
    fun `buildCommand includes scale and fps in vfParts not separate -vf`() {
        val opts = FFmpegProcessor.ProcessOptions(
            targetWidth = 720,
            targetFps = 30,
        )
        val ctx = mock(Context::class.java)
        val cmd = FFmpegProcessor.buildCommand("/tmp/input.mp4", "/tmp/output.mp4", opts, ctx, "/tmp/input.mp4")
        assertFalse(cmd.contains("-vf scale="))
        assertTrue(cmd.contains("scale=720:-2"))
        assertTrue(cmd.contains("fps=30"))
    }

    @Test
    fun `buildMultiClipCommand includes scale and fps in filter graph not separate -vf`() {
        val opts = FFmpegProcessor.ProcessOptions(
            targetWidth = 1080,
            targetFps = 60,
        )
        val ctx = mock(Context::class.java)
        val cmd = FFmpegProcessor.buildMultiClipCommand("/tmp/input.mp4", "/tmp/output.mp4", opts, ctx, "/tmp/input.mp4")
        assertFalse(cmd.contains("-vf scale="))
        assertTrue(cmd.contains("scale=1080:-2"))
        assertTrue(cmd.contains("fps=60"))
        assertFalse(cmd.contains("-vf "))
    }

    @Test
    fun `buildCommand does not duplicate scale and fps filters`() {
        val opts = FFmpegProcessor.ProcessOptions(
            targetWidth = 480,
            targetFps = 24,
        )
        val ctx = mock(Context::class.java)
        val cmd = FFmpegProcessor.buildCommand("/tmp/input.mp4", "/tmp/output.mp4", opts, ctx, "/tmp/input.mp4")
        val scaleCount = cmd.split("scale=").size - 1
        assertEquals(1, scaleCount)
        val fpsCount = cmd.split("fps=").size - 1
        assertEquals(1, fpsCount)
    }

    @Test
    fun `ProcessOptions defaults are correct`() {
        val opts = FFmpegProcessor.ProcessOptions()
        assertEquals("balanced", opts.outputQuality)
        assertEquals(0, opts.targetWidth)
        assertEquals(0, opts.targetFps)
    }
}