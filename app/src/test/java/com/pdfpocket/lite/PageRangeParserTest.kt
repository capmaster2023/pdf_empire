package com.pdfpocket.lite

import com.pdfpocket.lite.core.PageRangeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageRangeParserTest {

    @Test
    fun `page unique valide`() {
        assertEquals(listOf(3), PageRangeParser.parse("3", 10))
    }

    @Test
    fun `plage simple valide`() {
        assertEquals(listOf(1, 2, 3, 4), PageRangeParser.parse("1-4", 10))
    }

    @Test
    fun `combinaison pages et plages`() {
        assertEquals(listOf(1, 2, 3, 4, 8, 10), PageRangeParser.parse("1-4, 8, 10", 10))
    }

    @Test
    fun `doublons fusionnes et tries`() {
        assertEquals(listOf(2, 3, 4, 5), PageRangeParser.parse("3-5,2,4", 10))
    }

    @Test
    fun `page zero invalide`() {
        assertNull(PageRangeParser.parse("0", 10))
    }

    @Test
    fun `page au dela du document invalide`() {
        assertNull(PageRangeParser.parse("11", 10))
    }

    @Test
    fun `plage inversee invalide`() {
        assertNull(PageRangeParser.parse("5-2", 10))
    }

    @Test
    fun `texte non numerique invalide`() {
        assertNull(PageRangeParser.parse("abc", 10))
    }

    @Test
    fun `saisie vide invalide`() {
        assertNull(PageRangeParser.parse("", 10))
        assertNull(PageRangeParser.parse("   ", 10))
    }

    @Test
    fun `virgule orpheline invalide`() {
        assertNull(PageRangeParser.parse("1,,3", 10))
    }

    @Test
    fun `document sans pages invalide`() {
        assertNull(PageRangeParser.parse("1", 0))
    }
}
