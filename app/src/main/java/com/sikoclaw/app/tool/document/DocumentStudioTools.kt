package com.sikoclaw.app.tool.document

import android.net.Uri
import androidx.core.content.FileProvider
import com.sikoclaw.app.ClawApplication
import com.sikoclaw.app.tool.BaseTool
import com.sikoclaw.app.tool.ToolParameter
import com.sikoclaw.app.tool.ToolResult
import com.sikoclaw.app.ui.chat.ChatAttachment
import com.sikoclaw.app.ui.chat.ChatAttachmentManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Native, app-private document operations. Outputs are chat attachment markers, never Base64. */
class DocumentStudioTool(private val action: String, private val kind: String) : BaseTool() {
    override fun getName() = "${action}_${kind}"
    override fun getDisplayName() = "${action.replaceFirstChar(Char::uppercase)} ${kind.uppercase()}"
    override fun getDescriptionEN() = "$action a ${kind.uppercase()} document in app-private storage and return a chat attachment. Reading accepts a content URI from an uploaded attachment. Editing creates a safe revised copy and never overwrites the source."
    override fun getDescriptionCN() = getDescriptionEN()
    override fun getParameters(): List<ToolParameter> = when (action) {
        "create" -> listOf(ToolParameter("title", "string", "File title", true), ToolParameter("content", "string", "Document content; rows may be newline separated", true))
        "read" -> listOf(ToolParameter("uri", "string", "Attachment content URI or private file path", true))
        else -> listOf(ToolParameter("uri", "string", "Source attachment URI", true), ToolParameter("instructions", "string", "Replacement content or requested changes", true))
    }

    override fun execute(params: Map<String, Any>): ToolResult {
        if (!com.sikoclaw.app.plugin.PluginCatalog.isEnabled("document_studio")) return ToolResult.error("Document Studio plugin is disabled")
        return runCatching {
            when (action) {
                "create" -> create(requireString(params, "title"), requireString(params, "content"))
                "read" -> ToolResult.success(read(requireString(params, "uri")))
                else -> {
                    val original = read(requireString(params, "uri"))
                    create("edited_${kind}_${System.currentTimeMillis()}", "$original\n\nRequested revision:\n${requireString(params, "instructions")}")
                }
            }
        }.getOrElse { ToolResult.error("Document Studio ${getName()} failed: ${it.message}") }
    }

    private fun create(titleRaw: String, content: String): ToolResult {
        val title = titleRaw.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80).ifBlank { "document" }
        val dir = File(ClawApplication.instance.filesDir, "generated_files").apply {
            mkdirs()
            val cutoff = System.currentTimeMillis() - 14L * 24L * 60L * 60L * 1000L
            listFiles()?.filter { it.lastModified() < cutoff }?.forEach { runCatching { it.delete() } }
        }
        val file = File(dir, "${System.currentTimeMillis()}_${title.substringBeforeLast('.')}.$kind")
        when (kind) {
            "pdf" -> createPdf(file, content)
            "docx" -> createDocx(file, content)
            "xlsx" -> createXlsx(file, content)
            "pptx" -> createPptx(file, title, content)
        }
        val mime = mime(kind)
        val uri = FileProvider.getUriForFile(ClawApplication.instance, "${ClawApplication.instance.packageName}.fileprovider", file)
        val attachment = ChatAttachment(uri = uri.toString(), name = file.name, mimeType = mime, sizeBytes = file.length(), extractedText = content.take(80_000))
        return ToolResult.success("Document created. Include this exact marker in your reply: ${ChatAttachmentManager.encodeMarker(attachment)}")
    }

    private fun read(uriOrPath: String): String {
        val temp = when {
            uriOrPath.startsWith("content://") -> File.createTempFile("document_read_", ".$kind", ClawApplication.instance.cacheDir).also { out ->
                ClawApplication.instance.contentResolver.openInputStream(Uri.parse(uriOrPath))!!.use { input -> out.outputStream().use(input::copyTo) }
            }
            else -> File(uriOrPath).also { require(it.canonicalPath.startsWith(ClawApplication.instance.filesDir.canonicalPath)) { "Only app-private file paths are allowed" } }
        }
        return try {
            when (kind) {
                "pdf" -> { PDFBoxResourceLoader.init(ClawApplication.instance); PDDocument.load(temp).use { PDFTextStripper().getText(it) } }
                else -> extractXmlText(temp)
            }.take(80_000)
        } finally { if (temp.parentFile == ClawApplication.instance.cacheDir) temp.delete() }
    }

    private fun createPdf(file: File, content: String) {
        PDFBoxResourceLoader.init(ClawApplication.instance)
        PDDocument().use { document ->
            var page = PDPage(); document.addPage(page)
            var stream = PDPageContentStream(document, page); stream.setFont(PDType1Font.HELVETICA, 11f); stream.beginText(); stream.newLineAtOffset(48f, 780f)
            var lines = 0
            content.lineSequence().flatMap { wrap(it, 92).asSequence() }.forEach { line ->
                if (lines >= 48) { stream.endText(); stream.close(); page = PDPage(); document.addPage(page); stream = PDPageContentStream(document, page); stream.setFont(PDType1Font.HELVETICA, 11f); stream.beginText(); stream.newLineAtOffset(48f, 780f); lines = 0 }
                stream.showText(line.replace(Regex("[^\\x20-\\x7E]"), "?")); stream.newLineAtOffset(0f, -15f); lines++
            }
            stream.endText(); stream.close(); document.save(file)
        }
    }

    private fun createDocx(file: File, content: String) = zip(file, mapOf(
        "[Content_Types].xml" to """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""",
        "_rels/.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
        "word/document.xml" to """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>${content.lines().joinToString("") { "<w:p><w:r><w:t xml:space=\"preserve\">${xml(it)}</w:t></w:r></w:p>" }}<w:sectPr/></w:body></w:document>""",
    ))

    private fun createXlsx(file: File, content: String) = zip(file, mapOf(
        "[Content_Types].xml" to """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
        "_rels/.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
        "xl/workbook.xml" to """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>""",
        "xl/_rels/workbook.xml.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
        "xl/worksheets/sheet1.xml" to """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${content.lines().mapIndexed { i, row -> "<row r=\"${i+1}\">" + row.split('\t', ',').mapIndexed { j, cell -> "<c r=\"${('A'.code+j).toChar()}${i+1}\" t=\"inlineStr\"><is><t>${xml(cell)}</t></is></c>" }.joinToString("") + "</row>" }.joinToString("")}</sheetData></worksheet>""",
    ))

    private fun createPptx(file: File, title: String, content: String) = zip(file, mapOf(
        "[Content_Types].xml" to """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/><Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/></Types>""",
        "_rels/.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/></Relationships>""",
        "ppt/presentation.xml" to """<?xml version="1.0"?><p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst><p:sldSz cx="9144000" cy="6858000"/></p:presentation>""",
        "ppt/_rels/presentation.xml.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/></Relationships>""",
        "ppt/slides/slide1.xml" to """<?xml version="1.0"?><p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr/><p:grpSpPr/><p:sp><p:nvSpPr/><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>${xml(title)}</a:t></a:r></a:p><a:p><a:r><a:t>${xml(content)}</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>""",
    ))

    private fun zip(file: File, entries: Map<String, String>) = ZipOutputStream(file.outputStream()).use { out -> entries.forEach { (name, value) -> out.putNextEntry(ZipEntry(name)); out.write(value.toByteArray()); out.closeEntry() } }
    private fun extractXmlText(file: File): String = ZipInputStream(file.inputStream()).use { zip -> buildString { while (true) { val entry = zip.nextEntry ?: break; if (entry.name.endsWith(".xml")) append(String(zip.readBytes()).replace(Regex("<[^>]+>"), " ")).append('\n') } }.replace(Regex("\\s+"), " ").trim() }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun wrap(value: String, length: Int) = if (value.isEmpty()) listOf(" ") else value.chunked(length)
    private fun mime(kind: String) = when (kind) { "pdf" -> "application/pdf"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; else -> "application/vnd.openxmlformats-officedocument.presentationml.presentation" }
}

fun documentStudioTools(): List<BaseTool> = listOf("pdf", "docx", "xlsx", "pptx").flatMap { kind ->
    listOf("create", "read", "edit").map { action -> DocumentStudioTool(action, kind) }
}
