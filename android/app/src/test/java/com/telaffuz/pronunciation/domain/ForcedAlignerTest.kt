package com.telaffuz.pronunciation.domain

import com.telaffuz.pronunciation.testutil.id
import com.telaffuz.pronunciation.testutil.peakedLogProbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForcedAlignerTest {

    @Test
    fun `temiz hizalamada her fonem kendi karelerine oturur`() {
        val logProbs = peakedLogProbs("_", "k", "k", "_", "ae", "ae", "_", "t", "_")
        val spans = ForcedAligner.align(logProbs, intArrayOf(id("k"), id("ae"), id("t")))

        assertEquals(listOf(id("k"), id("ae"), id("t")), spans.map { it.phoneId })
        assertEquals(listOf(1 to 3, 4 to 6, 7 to 8), spans.map { it.startFrame to it.endFrame })
        spans.forEach { assertTrue("güven yüksek olmalı: ${it.confidence}", it.confidence >= 99) }
    }

    @Test
    fun `art arda ayni fonem araya blank girince iki ayri span olur`() {
        val logProbs = peakedLogProbs("l", "_", "l")
        val spans = ForcedAligner.align(logProbs, intArrayOf(id("l"), id("l")))

        assertEquals(2, spans.size)
        assertEquals(listOf(0 to 1, 2 to 3), spans.map { it.startFrame to it.endFrame })
    }

    @Test
    fun `hedef fonem sesde yoksa yine hizalanir ama guveni sifira yakin olur`() {
        // "ae" beklenirken "eh" söylenmiş.
        val logProbs = peakedLogProbs("k", "k", "eh", "eh", "t", "t")
        val spans = ForcedAligner.align(logProbs, intArrayOf(id("k"), id("ae"), id("t")))

        assertEquals(3, spans.size)
        val ae = spans[1]
        assertTrue(ae.startFrame >= 2 && ae.endFrame <= 4)
        assertTrue("güven düşük olmalı: ${ae.confidence}", ae.confidence < 5)
    }

    @Test
    fun `bos girdi bos liste doner`() {
        assertTrue(ForcedAligner.align(emptyArray(), intArrayOf(id("k"))).isEmpty())
        assertTrue(ForcedAligner.align(peakedLogProbs("k"), IntArray(0)).isEmpty())
    }
}
