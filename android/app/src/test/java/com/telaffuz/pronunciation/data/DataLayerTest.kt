package com.telaffuz.pronunciation.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DataLayerTest {

    @Test
    fun `wav basligi 44 byte ve dogru alanlari icerir`() {
        val header = WavCodec.buildHeader(pcmSize = 3200, sampleRate = 16000)

        assertEquals(44, header.size)
        assertEquals("RIFF", String(header, 0, 4))
        assertEquals("WAVE", String(header, 8, 4))
        assertEquals("data", String(header, 36, 4))
        assertEquals(3200 + 36, readIntLE(header, 4))
        assertEquals(16000, readIntLE(header, 24))
        assertEquals(32000, readIntLE(header, 28))
        assertEquals(3200, readIntLE(header, 40))
    }

    @Test
    fun `yazilan wav okununca ayni ornekler geri gelir`() {
        val shorts = shortArrayOf(0, 16384, -16384, Short.MAX_VALUE, Short.MIN_VALUE)
        val dir = createTempDir()
        try {
            val pcm = File(dir, "a.pcm").apply { writeBytes(shorts.toLeBytes()) }
            val wav = File(dir, "a.wav")
            WavCodec.writeWavFile(pcm, wav, 16000)

            val samples = WavCodec.readPcm16Wav(wav)
            assertArrayEquals(
                floatArrayOf(0f, 0.5f, -0.5f, 32767 / 32768f, -1f),
                samples,
                1e-6f
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `basliktan kisa dosya bos ornek dizisi verir`() {
        assertEquals(0, WavCodec.decodePcm16(ByteArray(10)).size)
    }

    @Test
    fun `ses seviyesi sessizlikte sifir yuksek seste bire kirpilir`() {
        assertEquals(0f, WavCodec.level(ByteArray(64), 64))
        val loud = ShortArray(32) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }.toLeBytes()
        assertEquals(1f, WavCodec.level(loud, loud.size))
        assertEquals(0f, WavCodec.level(ByteArray(0), 0))
    }

    @Test
    fun `cmu sozlugu satirlari kelime ve fonem listesine ayristirir`() {
        val dict = CmuDictionary { "cat K AE1 T\n\n  dog D AO1 G  \nbozuk\n".byteInputStream() }

        assertEquals(listOf("K", "AE1", "T"), dict.phonesFor("cat"))
        assertEquals(listOf("D", "AO1", "G"), dict.phonesFor("dog"))
        assertNull(dict.phonesFor("bozuk"))
        assertNull(dict.phonesFor("bird"))
    }

    @Test
    fun `cmu sozlugu dosyayi bir kez acar`() {
        var opens = 0
        val dict = CmuDictionary { opens++; "cat K AE1 T".byteInputStream() }
        repeat(3) { dict.phonesFor("cat") }
        assertEquals(1, opens)
    }

    private fun readIntLE(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    private fun ShortArray.toLeBytes(): ByteArray {
        val out = ByteArray(size * 2)
        forEachIndexed { i, s ->
            out[2 * i] = (s.toInt() and 0xff).toByte()
            out[2 * i + 1] = ((s.toInt() shr 8) and 0xff).toByte()
        }
        return out
    }

    private fun createTempDir(): File = java.nio.file.Files.createTempDirectory("wavtest").toFile()
}
