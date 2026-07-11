package com.pdfpocket.lite.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Export PDF vers formats Office, entièrement hors ligne :
 * - Word (.docx) et RTF : texte extrait, saut de page entre les pages du PDF ;
 * - Excel (.xlsx) : une ligne de texte par cellule, pages séparées ;
 * - PowerPoint (.pptx) : chaque page rendue en image sur sa propre diapositive.
 * Les fichiers docx/xlsx/pptx sont des ZIP d'XML générés directement
 * (structures validées contre python-docx, openpyxl et python-pptx).
 */
object OfficeExportToolbox {

    // ---------- Extraction du texte page par page ----------

    private fun extractPages(context: Context, source: Uri): List<String> {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Impossible de lire le document")
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                val pages = ArrayList<String>(document.numberOfPages)
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                for (index in 1..document.numberOfPages) {
                    stripper.startPage = index
                    stripper.endPage = index
                    val text = try {
                        stripper.getText(document)
                    } catch (_: Exception) {
                        ""
                    }
                    pages.add(text.trimEnd())
                }
                return pages
            }
        }
    }

    private fun escapeXml(text: String): String = buildString(text.length) {
        for (character in text) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(character)
            }
        }
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun openOutput(context: Context, output: Uri): OutputStream =
        context.contentResolver.openOutputStream(output)
            ?: throw IOException("Impossible d'ouvrir la destination")

    // ---------- Word (.docx) ----------

    @Throws(IOException::class)
    fun pdfToDocx(context: Context, source: Uri, output: Uri) {
        val pages = extractPages(context, source)
        openOutput(context, output).use { out ->
            ZipOutputStream(out).use { zip ->
                zip.entry(
                    "[Content_Types].xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
                )
                zip.entry(
                    "_rels/.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
                )
                val body = StringBuilder()
                pages.forEachIndexed { index, page ->
                    if (index > 0) {
                        body.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
                    }
                    for (line in page.split('\n')) {
                        body.append("""<w:p><w:r><w:t xml:space="preserve">""")
                            .append(escapeXml(line))
                            .append("""</w:t></w:r></w:p>""")
                    }
                }
                zip.entry(
                    "word/document.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr/></w:body></w:document>"""
                )
            }
        }
    }

    // ---------- RTF ----------

    @Throws(IOException::class)
    fun pdfToRtf(context: Context, source: Uri, output: Uri) {
        val pages = extractPages(context, source)
        openOutput(context, output).use { out ->
            val builder = StringBuilder()
            builder.append("""{\rtf1\ansi\deff0{\fonttbl{\f0 Helvetica;}}\f0\fs22 """)
            pages.forEachIndexed { index, page ->
                if (index > 0) builder.append("\\page ")
                for (line in page.split('\n')) {
                    builder.append(escapeRtf(line)).append("\\par ")
                }
            }
            builder.append('}')
            out.write(builder.toString().toByteArray(Charsets.US_ASCII))
        }
    }

    private fun escapeRtf(text: String): String = buildString(text.length) {
        for (character in text) {
            when {
                character == '\\' || character == '{' || character == '}' ->
                    append('\\').append(character)

                character.code in 32..126 -> append(character)

                else -> {
                    // Unicode RTF : entier signé 16 bits + caractère de repli.
                    var code = character.code
                    if (code > 32767) code -= 65536
                    append("\\u").append(code).append('?')
                }
            }
        }
    }

    // ---------- Excel (.xlsx) ----------

    @Throws(IOException::class)
    fun pdfToXlsx(context: Context, source: Uri, output: Uri) {
        val pages = extractPages(context, source)
        openOutput(context, output).use { out ->
            ZipOutputStream(out).use { zip ->
                zip.entry(
                    "[Content_Types].xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
                )
                zip.entry(
                    "_rels/.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
                )
                zip.entry(
                    "xl/workbook.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Texte" sheetId="1" r:id="rId1"/></sheets></workbook>"""
                )
                zip.entry(
                    "xl/_rels/workbook.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""
                )
                val rows = StringBuilder()
                var rowNumber = 1
                fun addRow(text: String) {
                    rows.append("""<row r="$rowNumber"><c r="A$rowNumber" t="inlineStr"><is><t xml:space="preserve">""")
                        .append(escapeXml(text))
                        .append("""</t></is></c></row>""")
                    rowNumber++
                }
                pages.forEachIndexed { index, page ->
                    addRow("— Page ${index + 1} —")
                    for (line in page.split('\n')) addRow(line)
                    rowNumber++ // ligne vide entre pages
                }
                zip.entry(
                    "xl/worksheets/sheet1.xml",
                    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>"""
                )
            }
        }
    }

    // ---------- PowerPoint (.pptx) : une diapo image par page ----------

    private const val EMU_PER_POINT = 12700L

    @Throws(IOException::class)
    fun pdfToPptx(context: Context, source: Uri, output: Uri) {
        // Copie locale pour PdfRenderer (descripteur seekable requis).
        val dir = File(context.cacheDir, "convert").apply { mkdirs() }
        val file = File(dir, "pptx_src_${System.currentTimeMillis()}.pdf")
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("Impossible de lire le document")
        input.use { stream ->
            FileOutputStream(file).use { fileOut -> stream.copyTo(fileOut) }
        }
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).use { renderer ->
                val pageCount = renderer.pageCount
                if (pageCount == 0) throw IOException("Document vide")
                // Taille des diapositives = taille de la première page (points -> EMU).
                var slideCx = 612L * EMU_PER_POINT
                var slideCy = 792L * EMU_PER_POINT
                renderer.openPage(0).use2 { first ->
                    slideCx = first.width.toLong() * EMU_PER_POINT
                    slideCy = first.height.toLong() * EMU_PER_POINT
                }
                openOutput(context, output).use { out ->
                    ZipOutputStream(out).use { zip ->
                        writePptxSkeleton(zip, pageCount, slideCx, slideCy)
                        for (index in 0 until pageCount) {
                            zip.entry(
                                "ppt/slides/slide${index + 1}.xml",
                                slideXml(slideCx, slideCy)
                            )
                            zip.entry(
                                "ppt/slides/_rels/slide${index + 1}.xml.rels",
                                slideRels(index + 1)
                            )
                            // L'image est compressée directement dans le flux ZIP.
                            zip.putNextEntry(ZipEntry("ppt/media/image${index + 1}.png"))
                            val bitmap = renderPage(renderer, index, targetWidth = 1400)
                            bitmap.compress(Bitmap.CompressFormat.PNG, 95, zip)
                            bitmap.recycle()
                            zip.closeEntry()
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    /** Équivalent de use{} pour PdfRenderer.Page (pas AutoCloseable avant API 31). */
    private inline fun <R> PdfRenderer.Page.use2(block: (PdfRenderer.Page) -> R): R {
        try {
            return block(this)
        } finally {
            try {
                close()
            } catch (_: Exception) {
            }
        }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int, targetWidth: Int): Bitmap {
        renderer.openPage(index).use2 { page ->
            val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    private fun writePptxSkeleton(
        zip: ZipOutputStream,
        pageCount: Int,
        cx: Long,
        cy: Long
    ) {
        val overrides = StringBuilder()
        for (index in 1..pageCount) {
            overrides.append("""<Override PartName="/ppt/slides/slide$index.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
        }
        zip.entry(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/><Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/><Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>$overrides</Types>"""
        )
        zip.entry(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>"""
        )
        val slideIds = StringBuilder()
        val presentationRels = StringBuilder()
        presentationRels.append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
        for (index in 0 until pageCount) {
            slideIds.append("""<p:sldId id="${256 + index}" r:id="rId${2 + index}"/>""")
            presentationRels.append("""<Relationship Id="rId${2 + index}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${index + 1}.xml"/>""")
        }
        zip.entry(
            "ppt/presentation.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst><p:sldIdLst>$slideIds</p:sldIdLst><p:sldSz cx="$cx" cy="$cy"/><p:notesSz cx="$cy" cy="$cx"/></p:presentation>"""
        )
        zip.entry(
            "ppt/_rels/presentation.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$presentationRels</Relationships>"""
        )
        zip.entry("ppt/theme/theme1.xml", THEME_XML)
        zip.entry("ppt/slideMasters/slideMaster1.xml", MASTER_XML)
        zip.entry("ppt/slideMasters/_rels/slideMaster1.xml.rels", MASTER_RELS_XML)
        zip.entry("ppt/slideLayouts/slideLayout1.xml", LAYOUT_XML)
        zip.entry("ppt/slideLayouts/_rels/slideLayout1.xml.rels", LAYOUT_RELS_XML)
    }

    private fun slideXml(cx: Long, cy: Long): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/><p:pic><p:nvPicPr><p:cNvPr id="2" name="Page"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>"""

    private fun slideRels(imageNumber: Int): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$imageNumber.png"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/></Relationships>"""

    private const val THEME_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="T"><a:themeElements><a:clrScheme name="C"><a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="44546A"/></a:dk2><a:lt2><a:srgbClr val="E7E6E6"/></a:lt2><a:accent1><a:srgbClr val="4472C4"/></a:accent1><a:accent2><a:srgbClr val="ED7D31"/></a:accent2><a:accent3><a:srgbClr val="A5A5A5"/></a:accent3><a:accent4><a:srgbClr val="FFC000"/></a:accent4><a:accent5><a:srgbClr val="5B9BD5"/></a:accent5><a:accent6><a:srgbClr val="70AD47"/></a:accent6><a:hlink><a:srgbClr val="0563C1"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink></a:clrScheme><a:fontScheme name="F"><a:majorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont></a:fontScheme><a:fmtScheme name="S"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln><a:ln><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"""

    private const val MASTER_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst></p:sldMaster>"""

    private const val MASTER_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/></Relationships>"""

    private const val LAYOUT_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank"><p:cSld name="Blank"><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"""

    private const val LAYOUT_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/></Relationships>"""
}
