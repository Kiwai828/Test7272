package com.recapmaker.app.media.rvc

import android.util.Log
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "Rvc.Wav"

object WavIo {
    private const val FORMAT_PCM = 1

    fun write(output: OutputStream, samples: FloatArray, sampleRate: Int) {
        Log.i(TAG, "write: ${samples.size} samples @ ${sampleRate}Hz")
        output.write(encodePcm16Mono(samples, sampleRate))
    }

    private fun encodePcm16Mono(samples: FloatArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val totalSize = 44 + dataSize
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalSize - 8)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(FORMAT_PCM.toShort())
        buf.putShort(1)
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in samples) {
            val v = (s.coerceIn(-1f, 1f) * 32767f).toInt()
            buf.putShort(v.toShort())
        }
        return buf.array()
    }
}
