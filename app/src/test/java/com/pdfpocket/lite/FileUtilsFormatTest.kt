package com.pdfpocket.lite

import com.pdfpocket.lite.core.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilsFormatTest {

    @Test
    fun `octets bruts`() {
        assertEquals("512 o", FileUtils.formatSize(512))
    }

    @Test
    fun `kilo octets`() {
        assertEquals("1.0 Ko", FileUtils.formatSize(1024))
    }

    @Test
    fun `mega octets`() {
        assertEquals("2.5 Mo", FileUtils.formatSize((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `giga octets`() {
        assertEquals("1.00 Go", FileUtils.formatSize(1024L * 1024 * 1024))
    }
}
