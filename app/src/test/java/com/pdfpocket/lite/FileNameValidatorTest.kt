package com.pdfpocket.lite

import com.pdfpocket.lite.core.FileNameValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameValidatorTest {

    @Test
    fun `nom simple valide`() {
        assertTrue(FileNameValidator.isValid("rapport.pdf"))
    }

    @Test
    fun `nom avec slash invalide`() {
        assertFalse(FileNameValidator.isValid("a/b.pdf"))
    }

    @Test
    fun `nom vide invalide`() {
        assertFalse(FileNameValidator.isValid("   "))
    }

    @Test
    fun `sanitize ajoute extension pdf`() {
        assertEquals("rapport.pdf", FileNameValidator.sanitize("rapport"))
    }

    @Test
    fun `sanitize conserve extension existante`() {
        assertEquals("rapport.pdf", FileNameValidator.sanitize("rapport.pdf"))
        assertEquals("Rapport.PDF", FileNameValidator.sanitize("Rapport.PDF"))
    }

    @Test
    fun `sanitize remplace caracteres illegaux`() {
        assertEquals("a_b_c.pdf", FileNameValidator.sanitize("a/b:c"))
    }

    @Test
    fun `sanitize nom vide donne document`() {
        assertEquals("document.pdf", FileNameValidator.sanitize(""))
        assertEquals("document.pdf", FileNameValidator.sanitize("   "))
    }
}
