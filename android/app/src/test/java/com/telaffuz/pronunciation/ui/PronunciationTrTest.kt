package com.telaffuz.pronunciation.ui

import com.telaffuz.pronunciation.domain.model.PhoneResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PronunciationTrTest {

    @Test
    fun `arpabet turkce okunusa cevrilir`() {
        assertEquals("ket", phonesToTrPlain(listOf("k", "ae", "t")))
        assertEquals("şip", phonesToTrPlain(listOf("sh", "ih", "p")))
    }

    @Test
    fun `w ve y kumeleri tek unluye indirgenir`() {
        assertEquals(listOf("uw"), simplifyPhonesPlain(listOf("ah", "w", "ah")))
        assertEquals(listOf("k", "iy"), simplifyPhonesPlain(listOf("k", "ah", "y")))
    }

    @Test
    fun `indirgenen kumenin guveni ortalama alinir`() {
        val units = simplifyPhonesWithConf(
            listOf(PhoneResult("ah", 90), PhoneResult("w", 30), PhoneResult("t", 70, lowSignal = true))
        )
        assertEquals(listOf("uw", "t"), units.map { it.phone })
        assertEquals(60, units[0].conf)
        assertEquals(true, units[1].lowSignal)
    }

    @Test
    fun `guven renk esikleri 40 ve 70`() {
        assertEquals(confColor(0), confColor(39))
        assertEquals(confColor(40), confColor(69))
        assertEquals(confColor(70), confColor(100))
        assertNotEquals(confColor(39), confColor(40))
        assertNotEquals(confColor(69), confColor(70))
    }
}
