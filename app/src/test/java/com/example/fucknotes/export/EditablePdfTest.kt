package com.example.fucknotes.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attachment update must leave the original bytes alone, point every new cross-reference
 * entry at a real object, chain the trailers, and give the parts back unchanged.
 */
class EditablePdfTest {

    /** A one-page PDF with a classic cross-reference table and exact offsets, as PdfDocument writes. */
    private fun minimalPdf(): ByteArray {
        val out = StringBuilder()
        val offsets = mutableListOf<Int>()
        out.append("%PDF-1.4\n%âãÏÓ\n")
        fun obj(number: Int, body: String) {
            offsets += out.length
            out.append("$number 0 obj\n$body\nendobj\n")
        }
        obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        obj(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R >>")
        val content = "0 0 1 rg 100 100 200 300 re f"
        obj(4, "<< /Length ${content.length} >>\nstream\n$content\nendstream")
        val xref = out.length
        out.append("xref\n0 5\n0000000000 65535 f \n")
        offsets.forEach { out.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", it)) }
        out.append("trailer\n<< /Size 5 /Root 1 0 R /ID [<0102> <0304>] >>\nstartxref\n$xref\n%%EOF\n")
        return out.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun text(bytes: ByteArray) = bytes.toString(Charsets.ISO_8859_1)

    @Test fun partsComeBackUnchangedAndTheOriginalIsUntouched() {
        val original = minimalPdf()
        val note = """{"app":"fucknotes","title":"Week 6"}""".toByteArray()
        val image = ByteArray(5000) { (it * 31 % 251).toByte() }
        val result = EditablePdf.embed(original, listOf(EditablePdf.Part("image-0001.jpg", image), EditablePdf.Part("note.json", note)))

        assertArrayEquals(original, result.copyOfRange(0, original.size))
        val parts = EditablePdf.read(result).associateBy { it.name }
        assertEquals(setOf("note.json", "image-0001.jpg"), parts.keys)
        assertArrayEquals(note, parts.getValue("note.json").bytes)
        assertArrayEquals(image, parts.getValue("image-0001.jpg").bytes)
        assertTrue(EditablePdf.read(original).isEmpty())
    }

    @Test fun theUpdateIsAWellFormedIncrementalSection() {
        val original = minimalPdf()
        val result = EditablePdf.embed(original, listOf(EditablePdf.Part("note.json", "{}".toByteArray())))
        val all = text(result)
        val update = all.substring(original.size)

        // The catalog is rewritten with the name tree, the new objects follow, numbered from /Size.
        assertTrue(update.contains("1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Names << /EmbeddedFiles << /Names [ (note.json) 6 0 R ] >> >> >>"))
        assertTrue(update.contains("5 0 obj\n<< /Type /EmbeddedFile"))
        assertTrue(update.contains("6 0 obj\n<< /Type /Filespec /F (note.json)"))

        // The trailer chains to the first table and the new startxref lands on "xref".
        val oldStart = Regex("startxref\\s+(\\d+)").find(text(original))!!.groupValues[1]
        assertTrue(update.contains("/Prev $oldStart"))
        assertTrue(update.contains("/Size 7"))
        assertTrue(update.contains("/ID [<0102> <0304>]"))
        val newStart = Regex("startxref\\s+(\\d+)\\s+%%EOF\\s*$").find(all)!!.groupValues[1].toInt()
        assertTrue(all.startsWith("xref", newStart))

        // Every entry in the new table points at exactly the object it lists.
        val table = all.substring(newStart, all.indexOf("trailer", newStart))
        val lines = table.lines().drop(1).filter { it.isNotBlank() }
        var i = 0
        while (i < lines.size) {
            val (start, count) = lines[i].split(" ").map { it.toInt() }
            for (k in 0 until count) {
                val entry = lines[i + 1 + k]
                assertEquals(20, entry.length + 1)
                val offset = entry.substring(0, 10).toInt()
                assertTrue("object ${start + k} at $offset", all.startsWith("${start + k} 0 obj", offset))
            }
            i += 1 + count
        }
    }

    @Test fun aPdfWithCrossReferenceStreamsIsDeclinedNotDamaged() {
        val fake = "%PDF-1.5\n1 0 obj\n<< /Type /Catalog >>\nendobj\n2 0 obj\n<< /Type /XRef /Size 3 >>\nstream\n\nendstream\nendobj\nstartxref\n40\n%%EOF\n"
            .toByteArray(Charsets.ISO_8859_1)
        try {
            EditablePdf.embed(fake, listOf(EditablePdf.Part("note.json", "{}".toByteArray())))
            throw AssertionError("expected UnsupportedPdfException")
        } catch (expected: EditablePdf.UnsupportedPdfException) {
            assertTrue(expected.message!!.contains("Cross-reference streams"))
        }
    }

    @Test fun partNamesAreKeptPlainSoThePdfStringNeedsNoEscaping() {
        try {
            EditablePdf.embed(minimalPdf(), listOf(EditablePdf.Part("we(ird).json", "{}".toByteArray())))
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) { /* good */ }
    }

    @Test fun largeCompressiblePartsAreDeflatedAndSmallOnesStoredAsIs() {
        val original = minimalPdf()
        val repetitive = ByteArray(20_000) { 'a'.code.toByte() }
        val result = EditablePdf.embed(original, listOf(EditablePdf.Part("note.json", repetitive)))
        assertTrue("deflated part makes a small file", result.size < original.size + 2_000)
        assertArrayEquals(repetitive, EditablePdf.read(result).single().bytes)
    }
}
