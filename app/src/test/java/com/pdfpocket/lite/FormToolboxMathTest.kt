package com.pdfpocket.lite

import com.pdfpocket.lite.pdf.FormToolbox
import com.pdfpocket.lite.pdf.SignaturePlacement
import org.junit.Assert.assertEquals
import org.junit.Test

class FormToolboxMathTest {

    @Test
    fun `signature centree en bas de page A4`() {
        // Page A4 : 595 x 842 pt. Signature de 200x100 px placee a 50% / 90%.
        val placement = SignaturePlacement(
            pageIndex = 0, xRatio = 0.5f, yTopRatio = 0.9f,
            widthRatio = 0.2f, isInitials = false
        )
        val box = FormToolbox.signatureBox(595f, 842f, 200, 100, placement)
        assertEquals(0.5f * 595f, box[0], 0.01f)          // x
        assertEquals(0.2f * 595f, box[2], 0.01f)          // largeur
        assertEquals(box[2] / 2f, box[3], 0.01f)          // hauteur = w * 100/200
        assertEquals(842f - 0.9f * 842f - box[3], box[1], 0.01f) // y (origine bas)
    }

    @Test
    fun `hauteur suit le ratio du bitmap`() {
        val placement = SignaturePlacement(0, 0f, 0f, 0.5f, false)
        val box = FormToolbox.signatureBox(600f, 800f, 300, 150, placement)
        assertEquals(300f, box[2], 0.01f)
        assertEquals(150f, box[3], 0.01f)
    }
}
