package com.pdfpocket.lite.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

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

/** Texte libre à incruster, en ratios de la page (0..1), origine haut-gauche. */
data class FreeTextPlacement(
    val pageIndex: Int,
    val xRatio: Float,
    val yTopRatio: Float,
    val heightRatio: Float,
    val text: String
)

object FormToolbox {

    /** Liste les champs de formulaire (texte, cases, listes) d'un PDF. Vide si pas de formulaire. */
    fun loadFields(file: File): List<FormFieldInfo> {
        val result = mutableListOf<FormFieldInfo>()
        try {
            PDDocument.load(file).use { document ->
                val acroForm = document.documentCatalog.acroForm ?: return emptyList()
                val terminals = collectTerminals(acroForm.fields)

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

    /** Enregistre le résultat final vers un URI SAF. Le fichier source n'est jamais modifié. */
    @Throws(IOException::class)
    fun applyAndSave(
        context: Context,
        sourceFile: File,
        textValues: Map<String, String>,
        checkValues: Map<String, Boolean>,
        choiceValues: Map<String, String>,
        signatures: List<Pair<SignaturePlacement, Bitmap>>,
        freeTexts: List<FreeTextPlacement> = emptyList(),
        output: Uri
    ) {
        val outputStream = context.contentResolver.openOutputStream(output)
            ?: throw IOException("Impossible d'ouvrir la destination")
        outputStream.use { out ->
            applyToStream(
                sourceFile, textValues, checkValues, choiceValues, signatures, freeTexts, out
            )
        }
    }

    /** Génère un fichier d'aperçu (valeurs aplaties, sans signatures) pour le rendu en direct. */
    @Throws(IOException::class)
    fun buildPreviewFile(
        sourceFile: File,
        textValues: Map<String, String>,
        checkValues: Map<String, Boolean>,
        choiceValues: Map<String, String>,
        outFile: File
    ) {
        FileOutputStream(outFile).use { out ->
            applyToStream(
                sourceFile, textValues, checkValues, choiceValues,
                emptyList(), emptyList(), out
            )
        }
    }

    /**
     * Cœur commun : remplit les champs, aplatit le formulaire (pour que le rendu système
     * affiche les valeurs), incruste les signatures, écrit dans [out].
     */
    @Throws(IOException::class)
    private fun applyToStream(
        sourceFile: File,
        textValues: Map<String, String>,
        checkValues: Map<String, Boolean>,
        choiceValues: Map<String, String>,
        signatures: List<Pair<SignaturePlacement, Bitmap>>,
        freeTexts: List<FreeTextPlacement>,
        out: OutputStream
    ) {
        PDDocument.load(sourceFile).use { document ->
            val acroForm = document.documentCatalog.acroForm
            if (acroForm != null) {
                // IMPORTANT : ne PAS activer needAppearances avant setValue().
                // Avec needAppearances=true, PDFBox saute la génération du flux
                // d'apparence du champ ; flatten() aplatit alors du vide et la
                // valeur devient invisible (champ blanc à l'écran).
                try {
                    acroForm.setNeedAppearances(false)
                } catch (_: Exception) {
                }
                for (field in collectTerminals(acroForm.fields)) {
                    val name = field.fullyQualifiedName ?: continue
                    try {
                        when (field) {
                            is PDTextField -> textValues[name]?.let { field.setValue(it) }
                            is PDCheckBox -> checkValues[name]?.let { checked ->
                                if (checked) field.check() else field.unCheck()
                            }
                            is PDChoice -> choiceValues[name]?.let {
                                if (it.isNotEmpty()) field.setValue(it)
                            }
                        }
                    } catch (_: Exception) {
                        // Champ sans police/apparence exploitable : on pose la valeur
                        // brute sans générer d'apparence, les lecteurs la reconstruiront.
                        try {
                            acroForm.setNeedAppearances(true)
                            when (field) {
                                is PDTextField -> textValues[name]?.let { field.setValue(it) }
                                is PDChoice -> choiceValues[name]?.let {
                                    if (it.isNotEmpty()) field.setValue(it)
                                }
                                else -> Unit
                            }
                        } catch (_: Exception) {
                        } finally {
                            try {
                                acroForm.setNeedAppearances(false)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                // Aplatir écrit les valeurs dans le contenu des pages : elles deviennent
                // visibles dans tous les lecteurs, y compris le rendu système Android.
                try {
                    acroForm.flatten()
                } catch (_: Exception) {
                    // En cas d'échec de l'aplatissement, on repasse en needAppearances
                    // pour que la plupart des lecteurs affichent quand même les valeurs.
                    try {
                        acroForm.setNeedAppearances(true)
                    } catch (_: Exception) {
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

            for (freeText in freeTexts) {
                if (freeText.pageIndex < 0 ||
                    freeText.pageIndex >= document.numberOfPages
                ) continue
                val page = document.getPage(freeText.pageIndex)
                val box = page.mediaBox
                val fontSize = (freeText.heightRatio * box.height).coerceAtLeast(4f)
                val font = PDType1Font.HELVETICA
                val x = freeText.xRatio * box.width
                // yTop = haut du texte ; la ligne de base PDF est ~0.8 * taille plus bas.
                val firstBaseline =
                    box.height - freeText.yTopRatio * box.height - fontSize * 0.8f
                try {
                    PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { stream ->
                        stream.beginText()
                        stream.setFont(font, fontSize)
                        stream.newLineAtOffset(x, firstBaseline)
                        freeText.text.split("\n").forEachIndexed { lineIndex, line ->
                            if (lineIndex > 0) {
                                stream.newLineAtOffset(0f, -fontSize * 1.2f)
                            }
                            try {
                                stream.showText(line)
                            } catch (_: Exception) {
                                // Caractère hors encodage Helvetica : on nettoie et on réessaie.
                                try {
                                    stream.showText(sanitizeForType1(line))
                                } catch (_: Exception) {
                                }
                            }
                        }
                        stream.endText()
                    }
                } catch (_: Exception) {
                    // Un texte récalcitrant ne doit pas bloquer l'enregistrement.
                }
            }

            document.save(out)
        }
    }

    /** Remplace les caractères non encodables en WinAnsi (Helvetica) par '?'. */
    private fun sanitizeForType1(text: String): String {
        val builder = StringBuilder(text.length)
        for (character in text) {
            builder.append(if (character.code in 32..255) character else '?')
        }
        return builder.toString()
    }

    private fun collectTerminals(fields: List<PDField>): List<PDTerminalField> {
        val terminals = mutableListOf<PDTerminalField>()
        fun collect(field: PDField) {
            when (field) {
                is PDNonTerminalField -> field.children.forEach { collect(it) }
                is PDTerminalField -> terminals.add(field)
            }
        }
        fields.forEach { collect(it) }
        return terminals
    }
}
