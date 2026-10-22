package com.example.fucknotes.media.pptx

import android.graphics.Color

/**
 * Colour resolution for DrawingML.
 *
 * A colour in a deck is rarely literal: it is a theme slot (`accent1`), reached through the
 * master's colour map (`tx1` → `dk1`), then tinted (`lumMod`/`lumOff`) and faded (`alpha`). This
 * resolves all of that to one ARGB int, and is pure so the maths can be tested on the JVM.
 */
class PptxColors(
    /** Theme scheme: `dk1`, `lt1`, `dk2`, `lt2`, `accent1`…`accent6`, `hlink`, `folHlink`. */
    private val scheme: Map<String, Int>,
    /** Master colour map: `tx1` → `dk1`, `bg1` → `lt1`, and so on. */
    private val clrMap: Map<String, String>,
) {
    /** Resolves a colour element (`a:srgbClr`, `a:schemeClr`, `a:sysClr`, `a:prstClr`) or null. */
    fun resolve(node: XmlNode?): Int? {
        node ?: return null
        val base: Int = when (node.name) {
            "a:srgbClr" -> node.attr("val")?.let { hex(it) } ?: return null
            "a:sysClr" -> node.attr("lastClr")?.let { hex(it) } ?: (if (node.attr("val") == "windowText") Color.BLACK else Color.WHITE)
            "a:schemeClr" -> schemeColor(node.attr("val") ?: return null) ?: return null
            "a:prstClr" -> preset(node.attr("val") ?: return null) ?: return null
            else -> return null
        }
        return modify(base, node)
    }

    /** The first colour child of a fill-like element (`a:solidFill`, `a:gradFill`'s first stop). */
    fun fromFill(fill: XmlNode?): Int? {
        fill ?: return null
        return when (fill.name) {
            "a:solidFill" -> resolve(fill.children.firstOrNull())
            "a:gradFill" -> fill.descendants("a:gs").firstOrNull()?.children?.firstOrNull()?.let { resolve(it) }
            else -> null
        }
    }

    fun schemeColor(name: String): Int? {
        val slot = clrMap[name] ?: name
        return scheme[slot]
    }

    private fun modify(base: Int, node: XmlNode): Int {
        var color = base
        var alpha = Color.alpha(base)
        node.children.forEach { mod ->
            val v = mod.attr("val")?.toFloatOrNull()?.div(100_000f)
            when (mod.name) {
                "a:alpha" -> if (v != null) alpha = (255 * v).toInt().coerceIn(0, 255)
                "a:lumMod" -> if (v != null) color = adjustLuminance(color, mul = v, add = 0f)
                "a:lumOff" -> if (v != null) color = adjustLuminance(color, mul = 1f, add = v)
                "a:shade" -> if (v != null) color = blend(color, Color.BLACK, 1f - v)
                "a:tint" -> if (v != null) color = blend(color, Color.WHITE, 1f - v)
            }
        }
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    companion object {
        fun hex(value: String): Int? {
            val clean = value.trim().removePrefix("#")
            if (clean.length != 6) return null
            return runCatching { (0xFF000000L or clean.toLong(16)).toInt() }.getOrNull()
        }

        /** HSL luminance scaled by [mul] then offset by [add], as PowerPoint tints its themes. */
        fun adjustLuminance(color: Int, mul: Float, add: Float): Int {
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(color, hsl)
            hsl[2] = (hsl[2] * mul + add).coerceIn(0f, 1f)
            return androidx.core.graphics.ColorUtils.HSLToColor(hsl) or (color and 0xFF000000.toInt())
        }

        fun blend(from: Int, to: Int, ratio: Float): Int =
            androidx.core.graphics.ColorUtils.blendARGB(from, to, ratio.coerceIn(0f, 1f))

        private fun preset(name: String): Int? = when (name.lowercase()) {
            "black" -> Color.BLACK
            "white" -> Color.WHITE
            "red" -> Color.RED
            "green" -> Color.GREEN
            "blue" -> Color.BLUE
            "yellow" -> Color.YELLOW
            "gray", "grey" -> Color.GRAY
            "ltgray", "ltgrey" -> Color.LTGRAY
            "dkgray", "dkgrey" -> Color.DKGRAY
            else -> null
        }

        /** Office's defaults, used when a deck ships no theme part. */
        val DefaultScheme: Map<String, Int> = mapOf(
            "dk1" to 0xFF000000.toInt(), "lt1" to 0xFFFFFFFF.toInt(),
            "dk2" to 0xFF44546A.toInt(), "lt2" to 0xFFE7E6E6.toInt(),
            "accent1" to 0xFF4472C4.toInt(), "accent2" to 0xFFED7D31.toInt(),
            "accent3" to 0xFFA5A5A5.toInt(), "accent4" to 0xFFFFC000.toInt(),
            "accent5" to 0xFF5B9BD5.toInt(), "accent6" to 0xFF70AD47.toInt(),
            "hlink" to 0xFF0563C1.toInt(), "folHlink" to 0xFF954F72.toInt(),
        )
        val DefaultClrMap: Map<String, String> = mapOf(
            "bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2",
            "accent1" to "accent1", "accent2" to "accent2", "accent3" to "accent3",
            "accent4" to "accent4", "accent5" to "accent5", "accent6" to "accent6",
            "hlink" to "hlink", "folHlink" to "folHlink",
        )
    }
}
