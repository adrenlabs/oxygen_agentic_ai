package com.oxygen.ai.pdf

import android.content.Context
import com.oxygen.ai.core.error.OxygenError
import com.oxygen.ai.core.logging.OxygenLog
import com.oxygen.ai.rag.DocumentParser
import com.oxygen.ai.rag.ParsedDocument
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

class PdfExtractor(context: Context) : DocumentParser {
    init {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
    }

    override fun supports(mimeType: String, fileName: String): Boolean {
        return mimeType == "application/pdf" || fileName.lowercase().endsWith(".pdf")
    }

    override fun parse(stream: InputStream, fileName: String, mimeType: String): ParsedDocument {
        return extract(stream, fileName)
    }

    fun extract(stream: InputStream, fileName: String, password: String? = null): ParsedDocument {
        try {
            val bytes = stream.readBytes()
            if (bytes.size >= 5 && bytes.decodeToString(0, 5, false).startsWith("%PDF").not() &&
                !bytes.copyOfRange(0, minOf(8, bytes.size)).toString(Charsets.ISO_8859_1).contains("PDF")
            ) {
                // still try; some PDFs have headers
            }
            PDDocument.load(bytes.inputStream(), password ?: "").use { doc ->
                if (doc.isEncrypted && doc.currentAccessPermission?.canExtractContent() == false) {
                    throw OxygenError.PdfExtractionFailed("Encrypted PDF does not allow extraction")
                }
                val stripper = PDFTextStripper()
                val pageStarts = ArrayList<Int>()
                val body = StringBuilder()
                val pages = doc.numberOfPages
                for (i in 1..pages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    pageStarts.add(body.length)
                    val pageText = stripper.getText(doc).trim()
                    body.append(pageText)
                    if (i != pages) body.append("\n\n")
                }
                val title = doc.documentInformation?.title ?: fileName
                OxygenLog.i("pdf", "Extracted $pages pages from $fileName")
                return ParsedDocument(
                    text = body.toString(),
                    pageCount = pages,
                    pageStarts = pageStarts,
                    metadata = mapOf("name" to fileName, "title" to (title ?: fileName)),
                )
            }
        } catch (e: InvalidPasswordException) {
            throw OxygenError.PdfExtractionFailed("PDF is password protected")
        } catch (e: OxygenError) {
            throw e
        } catch (e: Exception) {
            throw OxygenError.PdfExtractionFailed(e.message ?: "extraction failed")
        }
    }
}
