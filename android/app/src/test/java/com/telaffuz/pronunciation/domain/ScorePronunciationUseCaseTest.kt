package com.telaffuz.pronunciation.domain

import com.telaffuz.pronunciation.testutil.peakedLogits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScorePronunciationUseCaseTest {

    private val dictionary = PhonemeDictionary { word ->
        mapOf(
            "cat" to listOf("K", "AE1", "T"),
            "law" to listOf("L", "AO1"),
        )[word]
    }

    private fun useCase(vararg framePhones: String, onSamples: (FloatArray) -> Unit = {}) =
        ScorePronunciationUseCase(dictionary, AcousticModel { samples ->
            onSamples(samples)
            peakedLogits(*framePhones)
        })

    @Test
    fun `dogru telaffuz tam puan alir ve duyulan fonem hedefle ayni olur`() {
        val result = useCase("_", "k", "k", "_", "ae", "ae", "_", "t", "t", "_")
            .score("cat", FloatArray(100))

        assertEquals(10, result.humanTotal)
        val phones = result.words.single().phones
        assertEquals(listOf("k", "ae", "t"), phones.map { it.phone })
        assertEquals(listOf("k", "ae", "t"), phones.map { it.heard })
        phones.forEach {
            assertTrue(it.conf >= 99)
            assertFalse(it.lowSignal)
        }
    }

    @Test
    fun `karisabilen fonem soylenince sen satirinda o fonem gorunur ve puan duser`() {
        // "cat"in ae'si yerine eh ("ket") okunmuş — ikisi de aynı karışıklık grubunda.
        val result = useCase("k", "k", "eh+ae", "eh+ae", "eh+ae", "t", "t").score("cat", FloatArray(100))

        val ae = result.words.single().phones[1]
        assertEquals("ae", ae.phone)
        assertEquals("eh", ae.heard)
        assertTrue(ae.heardConf >= 99)
        assertTrue(ae.conf < 5)
        // (99 + ~0 + 99) / 3 ≈ 66 -> 10 üzerinden 7
        assertEquals(7, result.humanTotal)
    }

    @Test
    fun `cumle basindaki dusuk guvenli fonem kenar etkisi sayilip puana katilmaz`() {
        // k'nin yerinde ölçülebilir hiçbir şey yok (m baskın) ama bu ilk ses.
        val result = useCase("m+k", "m+k", "ae", "ae", "t", "t").score("cat", FloatArray(100))

        val k = result.words.single().phones[0]
        assertTrue(k.lowSignal)
        assertEquals(10, result.humanTotal)
    }

    @Test
    fun `stres rakamlari ve ao katlamasi model sozlugune cevrilir`() {
        val result = useCase("_", "l", "l", "aa", "aa", "_").score("law", FloatArray(10))

        assertEquals(listOf("l", "aa"), result.words.single().phones.map { it.phone })
    }

    @Test
    fun `noktalama ve buyuk harf temizlenir ama kelime orijinal haliyle raporlanir`() {
        val result = useCase("k", "ae", "t").score("  Cat!  ", FloatArray(10))

        assertEquals("Cat!", result.words.single().word)
    }

    @Test
    fun `sozlukte olmayan kelimeler tek seferde listelenir`() {
        val error = assertThrows(UnknownWordException::class.java) {
            useCase("k").score("cat xyzzy blorp", FloatArray(10))
        }
        assertEquals(listOf("xyzzy", "blorp"), error.words)
    }

    @Test
    fun `fonem cikmayan metin anlamli bir hata verir`() {
        assertThrows(IllegalArgumentException::class.java) {
            useCase("k").score("123 !!", FloatArray(10))
        }
    }

    @Test
    fun `modele iki uca sessizlik eklenmis ses gider`() {
        var received: FloatArray? = null
        useCase("k", "ae", "t", onSamples = { received = it }).score("cat", FloatArray(1000) { 0.5f })

        val pad = (16000 * 0.4f).toInt()
        val samples = requireNotNull(received)
        assertEquals(1000 + 2 * pad, samples.size)
        assertEquals(0f, samples[pad - 1])
        assertEquals(0.5f, samples[pad])
        assertEquals(0.5f, samples[pad + 999])
        assertEquals(0f, samples[pad + 1000])
    }
}
