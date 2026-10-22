package com.example.fucknotes.media.pptx

import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

/**
 * An opened `.pptx` package: the slide order, each slide with its layout, master and theme, and
 * the media the slides reference.
 *
 * A presentation is a zip of XML parts joined by relationship files. Everything a slide inherits
 * — placeholder positions, text styles, the colour scheme — lives up the chain slide → layout →
 * master → theme, so each [Slide] carries that chain resolved once.
 */
class PptxDeck private constructor(private val zip: ZipFile) : Closeable {

    /** One part with its relationships, targets already resolved to absolute zip paths. */
    class Part(val path: String, val xml: XmlNode, val rels: Map<String, Rel>) {
        fun relOfType(suffix: String): Rel? = rels.values.firstOrNull { it.type.endsWith(suffix) }
    }

    data class Rel(val id: String, val type: String, val target: String)

    class Slide(
        val part: Part,
        val layout: Part?,
        val master: Part?,
        val colors: PptxColors,
        val index: Int,
    ) {
        /** Shape trees to paint under the slide's own: master first, then layout. */
        val inheritedTrees: List<Part> get() = listOfNotNull(master, layout)
    }

    // Declared before init: Kotlin initialises properties in order, and init reads the cache.
    private val partCache = HashMap<String, Part?>()

    val slideWidthEmu: Long
    val slideHeightEmu: Long
    val slides: List<Slide>

    init {
        val presentation = part("ppt/presentation.xml") ?: error("Not a PowerPoint file: ppt/presentation.xml is missing")
        val size = presentation.xml.path("p:presentation", "p:sldSz") ?: presentation.xml.descendants("p:sldSz").firstOrNull()
        slideWidthEmu = size?.attr("cx")?.toLongOrNull()?.takeIf { it > 0 } ?: 12_192_000L
        slideHeightEmu = size?.attr("cy")?.toLongOrNull()?.takeIf { it > 0 } ?: 6_858_000L

        val ids = presentation.xml.descendants("p:sldId")
        val parts = mutableListOf<Part>()
        ids.forEach { id ->
            val rel = presentation.rels[id.attr("r:id") ?: return@forEach] ?: return@forEach
            part(rel.target)?.let { parts += it }
        }
        // A deck with an empty id list still has slides on disk: fall back to the files in order.
        if (parts.isEmpty()) {
            zip.entries().asSequence().map { it.name }
                .filter { Regex("ppt/slides/slide\\d+\\.xml").matches(it) }
                .sortedBy { it.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0 }
                .forEach { path -> part(path)?.let { parts += it } }
        }
        require(parts.isNotEmpty()) { "The presentation has no slides" }

        val themeCache = HashMap<String, Map<String, Int>>()
        var index = 0
        slides = parts.filter { it.xml.attr("show") != "0" }.map { slide ->
            val layout = slide.relOfType("/slideLayout")?.let { part(it.target) }
            val master = layout?.relOfType("/slideMaster")?.let { part(it.target) }
            val theme = master?.relOfType("/theme")?.let { rel ->
                themeCache.getOrPut(rel.target) { part(rel.target)?.let(::parseScheme) ?: PptxColors.DefaultScheme }
            } ?: PptxColors.DefaultScheme
            val clrMap = master?.xml?.child("p:clrMap")?.attrs ?: PptxColors.DefaultClrMap
            Slide(slide, layout, master, PptxColors(theme, clrMap), index++)
        }
    }

    /** Raw bytes of a part, or null when the package has no such entry. */
    fun bytes(path: String): ByteArray? =
        zip.getEntry(path)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }

    private fun part(path: String): Part? = partCache.getOrPut(path) {
        val entry = zip.getEntry(path) ?: return@getOrPut null
        val xml = runCatching { zip.getInputStream(entry).use { XmlNode.parse(it) } }.getOrNull() ?: return@getOrPut null
        val dir = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val relsPath = if (dir.isEmpty()) "_rels/$name.rels" else "$dir/_rels/$name.rels"
        val rels = HashMap<String, Rel>()
        zip.getEntry(relsPath)?.let { relsEntry ->
            runCatching { zip.getInputStream(relsEntry).use { XmlNode.parse(it) } }.getOrNull()
                ?.descendants("Relationship")?.forEach { r ->
                    val id = r.attr("Id") ?: return@forEach
                    val target = r.attr("Target") ?: return@forEach
                    if (r.attr("TargetMode") == "External") return@forEach
                    rels[id] = Rel(id, r.attr("Type").orEmpty(), resolve(dir, target))
                }
        }
        Part(path, xml, rels)
    }

    override fun close() = zip.close()

    companion object {
        fun open(file: File): PptxDeck = PptxDeck(ZipFile(file))

        /** Joins a part directory and a relationship target, honouring `..` and absolute targets. */
        internal fun resolve(dir: String, target: String): String {
            val raw = if (target.startsWith("/")) target.removePrefix("/") else if (dir.isEmpty()) target else "$dir/$target"
            val out = ArrayDeque<String>()
            raw.split('/').forEach { seg ->
                when (seg) {
                    "", "." -> Unit
                    ".." -> out.removeLastOrNull()
                    else -> out.addLast(seg)
                }
            }
            return out.joinToString("/")
        }

        private fun parseScheme(theme: Part): Map<String, Int> {
            val scheme = theme.xml.descendants("a:clrScheme").firstOrNull() ?: return PptxColors.DefaultScheme
            val out = PptxColors.DefaultScheme.toMutableMap()
            scheme.children.forEach { slot ->
                val name = slot.name.removePrefix("a:")
                val color = slot.children.firstOrNull() ?: return@forEach
                val value = when (color.name) {
                    "a:srgbClr" -> color.attr("val")?.let { PptxColors.hex(it) }
                    "a:sysClr" -> color.attr("lastClr")?.let { PptxColors.hex(it) }
                    else -> null
                }
                if (value != null) out[name] = value
            }
            return out
        }
    }
}
