package com.telaffuz.pronunciation.data

import java.io.File

/** Sabit formatlı (16kHz, mono, 16-bit PCM, 44 byte header) WAV yazma/okuma. */
object WavCodec {
    const val HEADER_SIZE = 44

    fun readPcm16Wav(file: File): FloatArray = decodePcm16(file.readBytes())

    fun decodePcm16(bytes: ByteArray): FloatArray {
        val sampleCount = maxOf(0, (bytes.size - HEADER_SIZE) / 2)
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

    fun writeWavFile(pcmFile: File, wavFile: File, sampleRate: Int) {
        val header = buildHeader(pcmFile.length(), sampleRate)
        wavFile.outputStream().use { out ->
            out.write(header)
            pcmFile.inputStream().use { it.copyTo(out) }
        }
    }

    fun buildHeader(pcmSize: Long, sampleRate: Int): ByteArray {
        val totalDataLen = pcmSize + 36
        val byteRate = sampleRate * 2 // 16-bit mono
        val header = ByteArray(HEADER_SIZE)

        fun writeString(offset: Int, s: String) {
            s.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        }
        fun writeIntLE(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
            header[offset + 2] = ((value shr 16) and 0xff).toByte()
            header[offset + 3] = ((value shr 24) and 0xff).toByte()
        }
        fun writeShortLE(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
        }

        writeString(0, "RIFF")
        writeIntLE(4, totalDataLen.toInt())
        writeString(8, "WAVE")
        writeString(12, "fmt ")
        writeIntLE(16, 16)
        writeShortLE(20, 1) // PCM
        writeShortLE(22, 1) // mono
        writeIntLE(24, sampleRate)
        writeIntLE(28, byteRate)
        writeShortLE(32, 2) // block align
        writeShortLE(34, 16) // bits per sample
        writeString(36, "data")
        writeIntLE(40, pcmSize.toInt())
        return header
    }

    /** 16-bit PCM örneklerinin RMS seviyesini 0..1 aralığına ölçekler (ses çubuğu animasyonu için). */
    fun level(buffer: ByteArray, length: Int): Float {
        var sumSquares = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val lo = buffer[i].toInt() and 0xFF
            val hi = buffer[i + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toInt()
            sumSquares += (sample * sample).toDouble()
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = kotlin.math.sqrt(sumSquares / count) / 32768.0
        return (rms * 6.0).toFloat().coerceIn(0f, 1f)
    }
}
