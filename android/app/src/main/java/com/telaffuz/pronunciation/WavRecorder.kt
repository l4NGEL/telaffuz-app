package com.telaffuz.pronunciation

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.sqrt

class WavRecorder(private val sampleRate: Int = 16000) {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var isRecording = false

    var lastFile: File? = null
        private set

    /** 0f (sessizlik) ile 1f (yüksek ses) arası anlık mikrofon seviyesi — konuşurken animasyon için. */
    var audioLevel by mutableFloatStateOf(0f)
        private set

    @SuppressLint("MissingPermission")
    fun start(outputFile: File) {
        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, sampleRate * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val pcmFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.pcm")
        lastFile = outputFile
        isRecording = true
        audioLevel = 0f
        audioRecord?.startRecording()

        recordingThread = thread {
            val buffer = ByteArray(bufferSize)
            pcmFile.outputStream().use { out ->
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        out.write(buffer, 0, read)
                        audioLevel = computeLevel(buffer, read)
                    }
                }
            }
            writeWavFile(pcmFile, outputFile, sampleRate)
            pcmFile.delete()
        }
    }

    fun stop() {
        isRecording = false
        recordingThread?.join()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioLevel = 0f
    }

    private fun computeLevel(buffer: ByteArray, length: Int): Float {
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
        val rms = sqrt(sumSquares / count) / 32768.0
        return (rms * 6.0).toFloat().coerceIn(0f, 1f)
    }

    private fun writeWavFile(pcmFile: File, wavFile: File, sampleRate: Int) {
        val header = buildWavHeader(pcmFile.length(), sampleRate)
        wavFile.outputStream().use { out ->
            out.write(header)
            pcmFile.inputStream().use { it.copyTo(out) }
        }
    }

    private fun buildWavHeader(pcmSize: Long, sampleRate: Int): ByteArray {
        val totalDataLen = pcmSize + 36
        val byteRate = sampleRate * 2 // 16-bit mono
        val header = ByteArray(44)

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
}
