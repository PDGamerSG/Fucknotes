package com.example.fucknotes.media.pptx

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * A small element tree for Office Open XML parts.
 *
 * The presentation parts are tiny next to the media they reference, and a slide is read once and
 * walked a handful of times (placeholders, inherited styles, pictures), so a tree is simpler and
 * cheaper than a pull loop per question. Names keep their prefixes (`p:sp`, `a:off`) because the
 * format uses them consistently and a namespace-aware parse would only add indirection.
 */
class XmlNode(
    val name: String,
    val attrs: Map<String, String>,
) {
    val children: MutableList<XmlNode> = mutableListOf()
    private val textBuilder = StringBuilder()

    /** The element's own character data, without descendants. */
    val text: String get() = textBuilder.toString()

    internal fun appendText(value: String) { textBuilder.append(value) }

    fun attr(name: String): String? = attrs[name]

    /** The first direct child with this name, or null. */
    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }

    /** The first direct child along a path of names, or null. */
    fun path(vararg names: String): XmlNode? {
        var node: XmlNode? = this
        for (n in names) node = node?.child(n) ?: return null
        return node
    }

    fun childrenNamed(name: String): List<XmlNode> = children.filter { it.name == name }

    /** Depth-first descendants with this name, in document order. */
    fun descendants(name: String): List<XmlNode> {
        val out = mutableListOf<XmlNode>()
        fun walk(node: XmlNode) {
            node.children.forEach { c ->
                if (c.name == name) out += c
                walk(c)
            }
        }
        walk(this)
        return out
    }

    companion object {
        fun parse(input: InputStream): XmlNode {
            val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
            parser.setInput(input, null)
            val stack = ArrayDeque<XmlNode>()
            var root: XmlNode? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val attrs = HashMap<String, String>(parser.attributeCount)
                        for (i in 0 until parser.attributeCount) attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                        val node = XmlNode(parser.name, attrs)
                        if (stack.isEmpty()) root = node else stack.last().children += node
                        stack.addLast(node)
                    }
                    XmlPullParser.TEXT -> if (stack.isNotEmpty()) stack.last().appendText(parser.text)
                    XmlPullParser.END_TAG -> stack.removeLastOrNull()
                }
                event = parser.next()
            }
            return root ?: error("Empty XML part")
        }
    }
}
