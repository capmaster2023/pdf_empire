package com.pdfpocket.lite.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File
import java.io.IOException

enum class FieldType { TEXT, CHECKBOX, CHOICE }

/** Champ de formulaire détecté. Coordonnées en points PDF, origine haut-gauche de la page. */
data class FormFieldInfo(
    val name: String,
    val pageIndex: Int,
    val x: Float,
    val yTop: Float,
    val width: Float,
    val height: Float,
    val type: FieldType,
    val initialValue: String,
    val checked: Boolean,
    val options: List<String>
)

/** Position d'une signature/paraphe, en ratios de la page (0..1), origine haut-gauche. */
data class SignaturePlacement(
    val pageIndex: Int,
    val xRatio: Float,
    val yTopRatio: Float,
    val widthRatio: Float,
    val isInitials: Boolean
)

object FormToolbox {

    /** Liste les champs de formulaire (texte, cases, listes) d'un PDF. Vide si pas de formulaire. */
    fun loadFields(file: File): List<FormFieldInfo> {
        val result = mutableListOf<FormFieldInfo>()
        try {
            PDDocument.load(file).use { document ->
                val acroForm = document.documentCatalog.acroForm ?: return emptyList()
                val terminals = mutableListOf<PDTerminalField>()
                fun collect(field: PDField) {
                    when (field) {
                        is PDNonTerminalField -> field.children.forEach { collect(it) }
                        is PDTerminalField -> terminals.add(field)
                    }
                }
                acroForm.fields.forEach { collect(it) }

                for (field in terminals) {
                    val type = when (field) {
                        is PDTextField -> FieldType.TEXT
                        is PDCheckBox -> FieldType.CHECKBOX
                        is PDChoice -> FieldType.CHOICE
                        else -> null
                    } ?: continue

                    val options = if (field is PDChoice) {
                        try {
                            field.optionsDisplayValues.filterNotNull()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else emptyList()

                    val checked = field is PDCheckBox && try {
                        field.isChecked
                    } catch (_: Exception) {
                        false
                    }

                    val value = try {
                        field.valueAsString ?: ""
                    } catch (_: Exception) {
                        ""
                    }

                    for (widget in field.widgets) {
                        val page = widget.page ?: continue
                        val pageIndex = document.pages.indexOf(page)
                        if (pageIndex < 0) continue
                        val rect = widget.rectangle ?: continue
                        val box = page.mediaBox
                        result.add(
                            FormFieldInfo(
                                name = field.fullyQualifiedName ?: continue,
                                pageIndex = pageIndex,
                                x = rect.lowerLeftX,
                                yTop = box.height - rect.upperRightY,
                                width = rect.width,
                                height = rect.height,
                                type = type,
                                initialValue = value,
                                checked = checked,
                                options = options
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result
    }

    /** Calcule la boîte (x, y, w, h) en points PDF (origine bas-gauche) d'une signature. */
    fun signatureBox(
        pageWidth: Float,
        pageHeight: Float,
        bitmapWidth: Int,
        bitmapHeight: Int,
        placement: SignaturePlacement
    ): FloatArray {
        val width = placement.widthRatio * pageWidth
        val height = width * bitmapHeight / bitmapWidth.coerceAtLeast(1)
        val x = placement.xRatio * pageWidth
        val y = pageHeight - placement.yTopRatio * pageHeight - height
        return floatArrayOf(x, y, width, height)
    }

    /**
     * Applique les valeurs de champs et incruste les signatures, puis enregistre
     * dans [output]. Le fichier source n'est jamais modifié.
     */
    @Throws(IOException::class)
    fun applyAndSave(
        context: Context,
        sourceFile: File,
        textValues: Map<String, String>,
        checkValues: Map<String, Boolean>,
        choiceValues: Map<String, String>,
        signatures: List<Pair<SignaturePlacement, Bitmap>>,
        output: Uri
    ) {
        PDDocument.load(sourceFile).use { document ->
            val acroForm = document.documentCatalog.acroForm
            if (acroForm != null &&
                (textValues.isNotEmpty() || checkValues.isNotEmpty() || choiceValues.isNotEmpty())
            ) {
                try {
                    acroForm.setNeedAppearances(true)
                } catch (_: Exception) {
                }
                val terminals = mutableListOf<PDTerminalField>()
                fun collect(field: PDField) {
                    when (field) {
                        is PDNonTerminalField -> field.children.forEach { collect(it) }
                        is PDTerminalField -> terminals.add(field)
                    }
                }
                acroForm.fields.forEach { collect(it) }

                for (field in terminals) {
                    val name = field.fullyQualifiedName ?: continue
                    try {
                        when (field) {
                            is PDTextField -> textValues[name]?.let { field.setValue(it) }
                            is PDCheckBox -> checkValues[name]?.let { checked ->
                                if (checked) field.check() else field.unCheck()
                            }
                            is PDChoice -> choiceValues[name]?.let { field.setValue(it) }
                        }
                    } catch (_: Exception) {
                        // Un champ récalcitrant ne doit pas bloquer le reste.
                    }
                }
            }

            for ((placement, bitmap) in signatures) {
                if (placement.pageIndex < 0 || placement.pageIndex >= document.numberOfPages) continue
                val page = document.getPage(placement.pageIndex)
                val box = page.mediaBox
                val coords = signatureBox(
                    box.width, box.height, bitmap.width, bitmap.height, placement
                )
                val image = LosslessFactory.createFromImage(document, bitmap)
                PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { stream ->
                    stream.drawImage(image, coords[0], coords[1], coords[2], coords[3])
                }
            }

            val outputStream = context.contentResolver.openOutputStream(output)
                ?: throw IOException("Impossible d'ouvrir la destination")
            outputStream.use { document.save(it) }
        }
    }
}
