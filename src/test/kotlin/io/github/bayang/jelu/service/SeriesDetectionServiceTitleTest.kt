package io.github.bayang.jelu.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeriesDetectionServiceTitleTest {
    @Test
    fun `detects Book N pattern`() {
        val hint = SeriesDetectionService.detectFromTitle("The Great Adventure Book 3")
        assertNotNull(hint)
        assertEquals(3.0, hint!!.suggestedPosition)
        assertEquals(SeriesDetectionService.HintReason.TITLE_PATTERN, hint.reason)
    }

    @Test
    fun `detects Book Word pattern`() {
        val hint = SeriesDetectionService.detectFromTitle("The Great Adventure Book Three")
        assertNotNull(hint)
        assertEquals(3.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects Vol pattern`() {
        val hint = SeriesDetectionService.detectFromTitle("Encyclopedia of Magic Vol. 2")
        assertNotNull(hint)
        assertEquals(2.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects Volume pattern`() {
        val hint = SeriesDetectionService.detectFromTitle("Encyclopedia of Magic Volume 4")
        assertNotNull(hint)
        assertEquals(4.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects hash pattern`() {
        val hint = SeriesDetectionService.detectFromTitle("Diary of a Wimpy Kid #5")
        assertNotNull(hint)
        assertEquals(5.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects Part N pattern`() {
        val hint = SeriesDetectionService.detectFromTitle("The Final Empire Part 2")
        assertNotNull(hint)
        assertEquals(2.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects Part Word pattern`() {
        val hint = SeriesDetectionService.detectFromTitle("The Final Empire Part Two")
        assertNotNull(hint)
        assertEquals(2.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects parenthesized number`() {
        val hint = SeriesDetectionService.detectFromTitle("A Storm of Swords (3)")
        assertNotNull(hint)
        assertEquals(3.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects trailing number`() {
        val hint = SeriesDetectionService.detectFromTitle("Harry Potter 7")
        assertNotNull(hint)
        assertEquals(7.0, hint!!.suggestedPosition)
    }

    @Test
    fun `no false positive on normal title`() {
        val hint = SeriesDetectionService.detectFromTitle("The Intelligent Investor")
        assertNull(hint)
    }

    @Test
    fun `detects Arabic juz pattern with ordinal`() {
        val hint =
            SeriesDetectionService.detectFromTitle(
                "تاريخ الإسلام - الجزء الثاني",
            )
        assertNotNull(hint)
        assertEquals(2.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects Arabic juz pattern with number`() {
        val hint =
            SeriesDetectionService.detectFromTitle(
                "تاريخ الإسلام - الجزء 3",
            )
        assertNotNull(hint)
        assertEquals(3.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects Arabic kitab pattern`() {
        val hint =
            SeriesDetectionService.detectFromTitle(
                "الكتاب الثالث من السلسلة",
            )
        assertNotNull(hint)
        assertEquals(3.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects Arabic mujallad pattern`() {
        val hint =
            SeriesDetectionService.detectFromTitle(
                "المجلد الأول",
            )
        assertNotNull(hint)
        assertEquals(1.0, hint!!.suggestedPosition)
    }

    @Test
    fun `detects Arabic abbreviated jeem pattern`() {
        val hint =
            SeriesDetectionService.detectFromTitle(
                "ديوان المتنبي ج2",
            )
        assertNotNull(hint)
        assertEquals(2.0, hint!!.suggestedPosition)
    }

    @Test
    fun `no false positive on plain Arabic title`() {
        val hint =
            SeriesDetectionService.detectFromTitle(
                "حياة في الإدارة",
            )
        assertNull(hint)
    }
}
