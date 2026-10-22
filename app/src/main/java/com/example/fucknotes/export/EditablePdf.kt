package com.example.fucknotes.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Puts files inside a PDF, and takes them out again.
 *
 * The parts are attached the way the PDF standard describes — as embedded files named in the
 * catalog's `/EmbeddedFiles` tree — by appending an incremental update: the original bytes are
 * untouched, a rewritten catalog and the new objects follow, then a second cross-reference table
 * whose trailer points back at the first. Every reader shows the pages as before; a reader with
 * an attachments panel also lists the parts. Only classic cross-reference tables are handled,
 * which is what Android's `PdfDocument` writes; anything else is declined so a document is never
 * damaged.
 *
 * Reading does not walk the structure. Each part's stream carries a `/FucknotesPart` key, and
 * that marker is what is searched for, so a file still reads after being copied anywhere, and
 * one rewritten by another tool simply yields nothing.
 */
object EditablePdf {
    const val PART_NOTE = "note.json"
    private const val MARKER = "/FucknotesPart"

    class Part(val name: String, val bytes: ByteArray)

    class UnsupportedPdfException(message: String) : IllegalStateException(message)

    fun embed(file: File, parts: List<Part>) {
        val out = embed(file.readBytes(), parts)
        file.writeBytes(out)
    }

    fun embed(original: ByteArray, parts: List<Part>): ByteArray {
        require(parts.isNotEmpty()) { "Nothing to embed" }
        require(parts.map { it.name }.toSet().size == parts.size) { "Part names must be unique" }
        parts.forEach { require(it.name.all { c -> c.code in 33..126 && c != '(' && c != ')' && c != '\\' }) { "Part name must be plain ASCII: ${it.name}" } }
        val structure = Structure.of(original)

        val catalogText = structure.catalogDict
        if (catalogText.contains("/Names")) throw UnsupportedPdfException("The PDF already carries a name tree")

        val sorted = parts.sortedBy { it.name }
        var next = structure.size
        val update = ByteArrayOutputStream()
        val offsets = sortedMapOf<Int, Long>()
        fun offsetNow() = original.size.toLong() + update.size()
        fun writeAscii(text: String) = update.write(text.toByteArray(Charsets.ISO_8859_1))

        // Names and their filespec objects, numbered before anything is written so the catalog
        // can refer to them.
        val streamNumbers = sorted.map { next++ }
        val specNumbers = sorted.map { next++ }
        val names = sorted.indices.joinToString(" ") { i -> "(${sorted[i].name}) ${specNumbers[i]} 0 R" }

        writeAscii("\n")
        offsets[structure.rootNumber] = offsetNow()
        val catalogBody = catalogText.substring(0, catalogText.length - 2).trimEnd()
        writeAscii("${structure.rootNumber} 0 obj\n$catalogBody /Names << /EmbeddedFiles << /Names [ $names ] >> >> >>\nendobj\n")

        sorted.forEachIndexed { i, part ->
            val deflated = deflate(part.bytes)
            val stored = if (deflated.size < part.bytes.size) deflated else part.bytes
            val filter = if (stored === deflated) " /Filter /FlateDecode" else ""
            offsets[streamNumbers[i]] = offsetNow()
            writeAscii(
                "${streamNumbers[i]} 0 obj\n<< /Type /EmbeddedFile /Subtype /application#2Foctet-stream" +
                    " /Length ${stored.size}$filter /Params << /Size ${part.bytes.size} >> $MARKER (${part.name}) >>\nstream\n"
            )
            update.write(stored)
            writeAscii("\nendstream\nendobj\n")
            offsets[specNumbers[i]] = offsetNow()
            writeAscii(
                "${specNumbers[i]} 0 obj\n<< /Type /Filespec /F (${part.name}) /UF (${part.name})" +
                    " /Desc (Fucknotes editable note data) /EF << /F ${streamNumbers[i]} 0 R /UF ${streamNumbers[i]} 0 R >> >>\nendobj\n"
            )
        }

        val xrefAt = offsetNow()
        writeAscii("xref\n")
        // Contiguous runs of object numbers become one subsection each.
        val numbers = offsets.keys.toList()
        var i = 0
        while (i < numbers.size) {
            var j = i
            while (j + 1 < numbers.size && numbers[j + 1] == numbers[j] + 1) j++
            writeAscii("${numbers[i]} ${j - i + 1}\n")
            for (k in i..j) writeAscii(String.format(java.util.Locale.ROOT, "%010d %05d n \n", offsets.getValue(numbers[k]), 0))
            i = j + 1
        }
        val extras = buildString {
            structure.info?.let { append(" /Info ").append(it) }
            structure.id?.let { append(" /ID ").append(it) }
        }
        writeAscii("trailer\n<< /Size $next /Root ${structure.rootNumber} 0 R /Prev ${structure.xrefOffset}$extras >>\nstartxref\n$xrefAt\n%%EOF\n")

        return original + update.toByteArray()
    }

    fun read(file: File): List<Part> = read(file.readBytes())

    /** Every part found, by name; empty when the file carries none or was rewritten past recognition. */
    fun read(bytes: ByteArray): List<Part> {
        val marker = MARKER.toByteArray(Charsets.ISO_8859_1)
        val found = linkedMapOf<String, Part>()
        var from = 0
        while (true) {
            val at = bytes.indexOf(marker, from)
            if (at < 0) break
            from = at + marker.size
            runCatching { partAt(bytes, at) }.getOrNull()?.let { found[it.name] = it }
        }
        return found.values.toList()
    }

    private fun partAt(bytes: ByteArray, markerAt: Int): Part? {
        // The marker sits in the stream's own dictionary, which holds a nested /Params dictionary
        // before it; start from the object header so the outer dictionary is the one matched.
        val objectAt = bytes.lastIndexOf(" 0 obj".toByteArray(), markerAt)
        if (objectAt < 0) return null
        val dictStart = bytes.indexOf("<<".toByteArray(), objectAt)
        if (dictStart < 0 || dictStart > markerAt) return null
        val dictEnd = matchDict(bytes, dictStart) ?: return null
        val dict = String(bytes, dictStart, dictEnd - dictStart, Charsets.ISO_8859_1)
        if (!dict.contains("/EmbeddedFile")) return null
        val name = Regex("""/FucknotesPart\s*\(([^)]*)\)""").find(dict)?.groupValues?.get(1) ?: return null
        val length = Regex("""/Length\s+(\d+)""").find(dict)?.groupValues?.get(1)?.toInt() ?: return null
        val streamAt = bytes.indexOf("stream".toByteArray(), dictEnd)
        if (streamAt < 0) return null
        var dataStart = streamAt + "stream".length
        if (bytes.getOrNull(dataStart) == '\r'.code.toByte()) dataStart++
        if (bytes.getOrNull(dataStart) == '\n'.code.toByte()) dataStart++
        if (dataStart + length > bytes.size) return null
        val raw = bytes.copyOfRange(dataStart, dataStart + length)
        val data = if (dict.contains("/FlateDecode")) inflate(raw) else raw
        return Part(name, data)
    }

    // ---- structure of the original file --------------------------------------------------------

    private class Structure(
        val xrefOffset: Long,
        val size: Int,
        val rootNumber: Int,
        val catalogDict: String,
        val info: String?,
        val id: String?,
    ) {
        companion object {
            fun of(bytes: ByteArray): Structure {
                if (!bytes.startsWith("%PDF-".toByteArray())) throw UnsupportedPdfException("Not a PDF")
                val startxref = bytes.lastIndexOf("startxref".toByteArray(), bytes.size - 1)
                if (startxref < 0) throw UnsupportedPdfException("No startxref")
                val xrefOffset = readInt(bytes, startxref + "startxref".length)?.toLong()
                    ?: throw UnsupportedPdfException("Unreadable startxref")
                if (xrefOffset <= 0 || xrefOffset >= bytes.size) throw UnsupportedPdfException("startxref out of range")
                var p = skipSpace(bytes, xrefOffset.toInt())
                if (!bytes.startsWith("xref".toByteArray(), p)) throw UnsupportedPdfException("Cross-reference streams are not supported")
                p += 4
                // Walk the subsections to find where the catalog object lives.
                val objectOffsets = mutableMapOf<Int, Long>()
                while (true) {
                    p = skipSpace(bytes, p)
                    if (bytes.startsWith("trailer".toByteArray(), p)) break
                    val start = readInt(bytes, p) ?: throw UnsupportedPdfException("Bad xref subsection")
                    p = skipToken(bytes, p)
                    val count = readInt(bytes, skipSpace(bytes, p)) ?: throw UnsupportedPdfException("Bad xref subsection")
                    p = skipToken(bytes, skipSpace(bytes, p))
                    p = skipSpace(bytes, p)
                    repeat(count) { k ->
                        val offset = readInt(bytes, p)?.toLong() ?: throw UnsupportedPdfException("Bad xref entry")
                        val type = bytes[p + 17].toInt().toChar()
                        if (type == 'n') objectOffsets[start + k] = offset
                        p += 20
                        // Some writers end entries with "\r\n", some with " \n"; both are 20 bytes.
                    }
                }
                val trailerDictStart = bytes.indexOf("<<".toByteArray(), p)
                val trailerDictEnd = matchDict(bytes, trailerDictStart) ?: throw UnsupportedPdfException("Bad trailer")
                val trailer = String(bytes, trailerDictStart, trailerDictEnd - trailerDictStart, Charsets.ISO_8859_1)
                val size = Regex("""/Size\s+(\d+)""").find(trailer)?.groupValues?.get(1)?.toInt()
                    ?: throw UnsupportedPdfException("Trailer has no /Size")
                val root = Regex("""/Root\s+(\d+)\s+(\d+)\s+R""").find(trailer)?.groupValues?.get(1)?.toInt()
                    ?: throw UnsupportedPdfException("Trailer has no /Root")
                val info = Regex("""/Info\s+(\d+\s+\d+\s+R)""").find(trailer)?.groupValues?.get(1)
                val id = Regex("""/ID\s*(\[.*?\])""", RegexOption.DOT_MATCHES_ALL).find(trailer)?.groupValues?.get(1)

                val catalogAt = objectOffsets[root]?.toInt()
                    ?: findObject(bytes, root)
                    ?: throw UnsupportedPdfException("Catalog object not found")
                var c = skipSpace(bytes, catalogAt)
                val objHeader = Regex("""^(\d+)\s+(\d+)\s+obj""").find(String(bytes, c, minOf(32, bytes.size - c), Charsets.ISO_8859_1))
                    ?: throw UnsupportedPdfException("Catalog offset does not point at an object")
                if (objHeader.groupValues[1].toInt() != root) throw UnsupportedPdfException("Catalog offset points at another object")
                c += objHeader.value.length
                val dictStart = bytes.indexOf("<<".toByteArray(), c)
                val dictEnd = matchDict(bytes, dictStart) ?: throw UnsupportedPdfException("Bad catalog dictionary")
                val dict = String(bytes, dictStart, dictEnd - dictStart, Charsets.ISO_8859_1)
                if (!dict.contains("/Catalog")) throw UnsupportedPdfException("Root object is not a catalog")
                return Structure(xrefOffset, size, root, dict, info, id)
            }

            private fun findObject(bytes: ByteArray, number: Int): Int? {
                val needle = "\n$number 0 obj".toByteArray()
                val at = bytes.lastIndexOf(needle, bytes.size - 1)
                return if (at < 0) null else at + 1
            }
        }
    }

    // ---- byte helpers ------------------------------------------------------------------------------

    /** Index just past the `>>` that closes the dictionary opened at [start], or null. */
    private fun matchDict(bytes: ByteArray, start: Int): Int? {
        if (start < 0 || !bytes.startsWith("<<".toByteArray(), start)) return null
        var depth = 0
        var i = start
        while (i < bytes.size - 1) {
            when {
                bytes[i] == '<'.code.toByte() && bytes[i + 1] == '<'.code.toByte() -> { depth++; i += 2; continue }
                bytes[i] == '>'.code.toByte() && bytes[i + 1] == '>'.code.toByte() -> {
                    depth--; i += 2
                    if (depth == 0) return i
                    continue
                }
                bytes[i] == '('.code.toByte() -> {
                    // Skip a string literal, honouring escapes and nesting.
                    var nest = 1; i++
                    while (i < bytes.size && nest > 0) {
                        when (bytes[i]) {
                            '\\'.code.toByte() -> i++
                            '('.code.toByte() -> nest++
                            ')'.code.toByte() -> nest--
                        }
                        i++
                    }
                    continue
                }
            }
            i++
        }
        return null
    }

    private fun skipSpace(bytes: ByteArray, from: Int): Int {
        var i = from
        while (i < bytes.size && bytes[i].toInt().toChar().isWhitespace()) i++
        return i
    }

    private fun skipToken(bytes: ByteArray, from: Int): Int {
        var i = from
        while (i < bytes.size && !bytes[i].toInt().toChar().isWhitespace()) i++
        return i
    }

    private fun readInt(bytes: ByteArray, from: Int): Int? {
        var i = skipSpace(bytes, from)
        val start = i
        while (i < bytes.size && bytes[i] in '0'.code.toByte()..'9'.code.toByte()) i++
        if (i == start) return null
        return String(bytes, start, i - start, Charsets.ISO_8859_1).toIntOrNull()
    }

    private fun ByteArray.startsWith(prefix: ByteArray, at: Int = 0): Boolean {
        if (at < 0 || at + prefix.size > size) return false
        for (i in prefix.indices) if (this[at + i] != prefix[i]) return false
        return true
    }

    private fun ByteArray.indexOf(needle: ByteArray, from: Int): Int {
        if (needle.isEmpty() || from < 0) return -1
        var i = from
        val last = size - needle.size
        outer@ while (i <= last) {
            for (j in needle.indices) if (this[i + j] != needle[j]) { i++; continue@outer }
            return i
        }
        return -1
    }

    private fun ByteArray.lastIndexOf(needle: ByteArray, from: Int): Int {
        var i = minOf(from, size - needle.size)
        outer@ while (i >= 0) {
            for (j in needle.indices) if (this[i + j] != needle[j]) { i--; continue@outer }
            return i
        }
        return -1
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(data); deflater.finish()
        val out = ByteArrayOutputStream(data.size / 2 + 64)
        val buffer = ByteArray(64 * 1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 3 + 64)
        val buffer = ByteArray(64 * 1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }
}
