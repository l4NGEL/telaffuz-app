package com.telaffuz.pronunciation

import java.io.File

/** WavRecorder'ın ürettiği sabit formatlı (16kHz, mono, 16-bit PCM, 44 byte header) WAV dosyalarını okur. */
object WavUtils {
    private const val HEADER_SIZE = 44

    fun readPcm16Wav(file: File): FloatArray {
        val bytes = file.readBytes()
        val sampleCount = (bytes.size - HEADER_SIZE) / 2
        val samples = FloatArray(sampleCount)
        var byteIdx = HEADER_SIZE
        for (i in 0 until sampleCount) {
            val lo = bytes[byteIdx].toInt() and 0xFF
            val hi = bytes[byteIdx + 1].toInt() and 0xFF
            val value = ((hi shl 8) or lo).toShort()
            samples[i] = value / 32768f
            byteIdx += 2
        }
        return samples
    }

    fun padAudio(samples: FloatArray, sampleRate: Int = 16000, padSeconds: Float = 0.4f): FloatArray {
        val padSamples = (sampleRate * padSeconds).toInt()
        val result = FloatArray(samples.size + 2 * padSamples)
        samples.copyInto(result, padSamples)
        return result
    }
}
