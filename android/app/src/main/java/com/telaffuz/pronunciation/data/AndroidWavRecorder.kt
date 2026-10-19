package com.telaffuz.pronunciation.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.telaffuz.pronunciation.domain.AudioRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.concurrent.thread

/** AudioRecord ile 16kHz mono PCM kaydeder; kayıt bitince WAV'a yazıp örnekleri geri okur. */
class AndroidWavRecorder(
    private val cacheDir: File,
    private val sampleRate: Int = 16000,
) : AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var currentFile: File? = null

    @Volatile
    private var isRecording = false

    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()

    @SuppressLint("MissingPermission")
    override fun start() {
        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, sampleRate * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord = record

        val outputFile = File(cacheDir, "kayit_${System.currentTimeMillis()}.wav")
        val pcmFile = File(cacheDir, "${outputFile.nameWithoutExtension}.pcm")
        currentFile = outputFile
        isRecording = true
        _level.value = 0f
        record.startRecording()

        recordingThread = thread {
            val buffer = ByteArray(bufferSize)
            pcmFile.outputStream().use { out ->
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        out.write(buffer, 0, read)
                        _level.value = WavCodec.level(buffer, read)
                    }
                }
            }
            WavCodec.writeWavFile(pcmFile, outputFile, sampleRate)
            pcmFile.delete()
        }
    }

    override fun stop(): FloatArray {
        isRecording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        _level.value = 0f

        val file = currentFile ?: return FloatArray(0)
        currentFile = null
        return try {
            WavCodec.readPcm16Wav(file)
        } finally {
            file.delete()
        }
    }
}
