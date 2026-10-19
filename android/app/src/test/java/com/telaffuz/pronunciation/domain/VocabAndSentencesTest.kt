package com.telaffuz.pronunciation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VocabAndSentencesTest {

    @Test
    fun `arpabet stres rakamlarini atar ve ao'yu aa'ya katlar`() {
        assertEquals(Vocab.phoneToId["ae"], Vocab.arpabetToId("AE1"))
        assertEquals(Vocab.phoneToId["ah"], Vocab.arpabetToId("AH0"))
        assertEquals(Vocab.phoneToId["aa"], Vocab.arpabetToId("AO2"))
        assertNull(Vocab.arpabetToId("XX"))
    }

    @Test
    fun `fonem idleri blank ve rezerve 1 disinda vokabulere sigar`() {
        assertEquals(38, Vocab.phoneToId.size)
        assertEquals((2 until Vocab.VOCAB_SIZE).toSet(), Vocab.phoneToId.values.toSet())
        assertTrue(Vocab.BLANK_ID !in Vocab.phoneToId.values)
    }

    @Test
    fun `karisiklik grubu fonemin kendisini icerir`() {
        Vocab.phoneToId.keys.forEach { assertTrue(it in Vocab.confusionSetFor(it)) }
        assertEquals(setOf("p", "t", "k"), Vocab.confusionSetFor("t"))
    }

    @Test
    fun `cumle saglayici bir oncekini art arda tekrar etmez`() {
        val provider = RandomSentenceProvider(listOf("A", "B", "C"), Random(42))
        var previous = provider.next(exclude = null)
        repeat(200) {
            val next = provider.next(exclude = previous)
            assertNotEquals(previous, next)
            previous = next
        }
    }

    @Test
    fun `tek cumlelik listede ayni cumle doner`() {
        assertEquals("A", RandomSentenceProvider(listOf("A")).next(exclude = "A"))
    }

    @Test
    fun `hazir cumlelerin hepsi bos olmayan buyuk harf metin`() {
        PRACTICE_SENTENCES.forEach {
            assertTrue(it.isNotBlank())
            assertEquals(it.uppercase(), it)
        }
    }
}
