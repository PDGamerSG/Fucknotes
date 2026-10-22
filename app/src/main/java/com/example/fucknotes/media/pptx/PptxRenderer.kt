package com.example.fucknotes.media.pptx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paints one slide onto a canvas whose unit is the PDF point.
 *
 * This is a deliberately partial PresentationML renderer: backgrounds, the master and layout
 * decoration, placeholder geometry and text styles inherited down the chain, text with runs,
 * bullets, alignment and auto-fit, pictures with cropping, preset shapes with theme fills and
 * outlines, groups, connectors and tables. Charts, SmartArt and embedded objects are drawn as a
 * labelled frame rather than dropped, so the reader can see that something was there.
 */
class PptxRenderer(private val deck: PptxDeck) {

    private val images = HashMap<String, Bitmap?>()

    fun render(slide: PptxDeck.Slide, canvas: Canvas) {
        val width = pt(deck.slideWidthEmu)
        val height = pt(deck.slideHeightEmu)
        drawBackground(slide, canvas, width, height)
        val showMaster = slide.part.xml.attr("showMasterSp") != "0"
        if (showMaster) slide.inheritedTrees.forEach { part ->
            spTree(part)?.let { renderChildren(it, slide, canvas, templates = true) }
        }
        spTree(slide.part)?.let { renderChildren(it, slide, canvas, templates = false) }
    }

    fun recycle() {
        images.values.forEach { it?.recycle() }
        images.clear()
    }

    // ---- background --------------------------------------------------------

    private fun drawBackground(slide: PptxDeck.Slide, canvas: Canvas, width: Float, height: Float) {
        val bg = listOfNotNull(slide.part, slide.layout, slide.master)
            .firstNotNullOfOrNull { it.xml.descendants("p:bg").firstOrNull() }
        var color = Color.WHITE
        var picture: Bitmap? = null
        if (bg != null) {
            val props = bg.child("p:bgPr")
            val fill = props?.children?.firstOrNull { it.name == "a:solidFill" || it.name == "a:gradFill" || it.name == "a:blipFill" }
            when (fill?.name) {
                "a:solidFill", "a:gradFill" -> slide.colors.fromFill(fill)?.let { color = it }
                "a:blipFill" -> picture = fill.child("a:blip")?.attr("r:embed")?.let { bitmapFor(slide.part, it) }
            }
            bg.child("p:bgRef")?.let { ref ->
                if (ref.attr("idx") != "0") slide.colors.resolve(ref.children.firstOrNull())?.let { color = it }
            }
        }
        canvas.drawColor(color)
        picture?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, width, height), bitmapPaint) }
    }

    // ---- shape tree --------------------------------------------------------

    private fun spTree(part: PptxDeck.Part): XmlNode? = part.xml.descendants("p:spTree").firstOrNull()

    /** [templates] is true for master/layout trees, whose placeholders are moulds, not content. */
    private fun renderChildren(tree: XmlNode, slide: PptxDeck.Slide, canvas: Canvas, templates: Boolean) {
        tree.children.forEach { node ->
            runCatching {
                when (node.name) {
                    "p:sp" -> if (!(templates && isPlaceholder(node))) renderShape(node, slide, canvas)
                    "p:pic" -> if (!(templates && node.path("p:nvPicPr", "p:nvPr", "p:ph") != null)) renderPicture(node, slide, canvas)
                    "p:grpSp" -> renderGroup(node, slide, canvas, templates)
                    "p:graphicFrame" -> if (!templates) renderGraphicFrame(node, slide, canvas)
                    "p:cxnSp" -> renderConnector(node, slide, canvas)
                }
            }
        }
    }

    private fun isPlaceholder(sp: XmlNode) = sp.path("p:nvSpPr", "p:nvPr", "p:ph") != null

    private fun renderGroup(group: XmlNode, slide: PptxDeck.Slide, canvas: Canvas, templates: Boolean) {
        val xfrm = group.path("p:grpSpPr", "a:xfrm")
        canvas.save()
        if (xfrm != null) {
            val off = xfrm.child("a:off"); val ext = xfrm.child("a:ext")
            val chOff = xfrm.child("a:chOff"); val chExt = xfrm.child("a:chExt")
            val x = pt(off?.attr("x")); val y = pt(off?.attr("y"))
            val cx = pt(ext?.attr("cx")); val cy = pt(ext?.attr("cy"))
            val chx = pt(chOff?.attr("x")); val chy = pt(chOff?.attr("y"))
            val chcx = pt(chExt?.attr("cx")); val chcy = pt(chExt?.attr("cy"))
            val rot = xfrm.attr("rot")?.toFloatOrNull()?.div(60_000f) ?: 0f
            if (rot != 0f) canvas.rotate(rot, x + cx / 2f, y + cy / 2f)
            canvas.translate(x, y)
            if (chcx > 0f && chcy > 0f) canvas.scale(cx / chcx, cy / chcy)
            canvas.translate(-chx, -chy)
        }
        renderChildren(group, slide, canvas, templates)
        canvas.restore()
    }

    // ---- geometry ----------------------------------------------------------

    private class Frame(val rect: RectF, val rot: Float, val flipH: Boolean, val flipV: Boolean)

    private fun frameOf(xfrm: XmlNode?): Frame? {
        xfrm ?: return null
        val off = xfrm.child("a:off") ?: return null
        val ext = xfrm.child("a:ext") ?: return null
        val x = pt(off.attr("x")); val y = pt(off.attr("y"))
        val w = pt(ext.attr("cx")); val h = pt(ext.attr("cy"))
        return Frame(
            RectF(x, y, x + w, y + h),
            xfrm.attr("rot")?.toFloatOrNull()?.div(60_000f) ?: 0f,
            xfrm.attr("flipH") == "1",
            xfrm.attr("flipV") == "1",
        )
    }

    /** Applies rotation and flips around the frame's centre; pair with [Canvas.restore]. */
    private fun enter(canvas: Canvas, frame: Frame) {
        canvas.save()
        val cx = frame.rect.centerX(); val cy = frame.rect.centerY()
        if (frame.rot != 0f) canvas.rotate(frame.rot, cx, cy)
        if (frame.flipH || frame.flipV) canvas.scale(if (frame.flipH) -1f else 1f, if (frame.flipV) -1f else 1f, cx, cy)
    }

    private fun placeholderChain(sp: XmlNode, slide: PptxDeck.Slide): List<XmlNode> {
        val ph = sp.path("p:nvSpPr", "p:nvPr", "p:ph") ?: return emptyList()
        val out = mutableListOf<XmlNode>()
        slide.layout?.let { findPlaceholder(it, ph, byIndex = true)?.let { found -> out += found } }
        slide.master?.let { findPlaceholder(it, ph, byIndex = false)?.let { found -> out += found } }
        return out
    }

    private fun findPlaceholder(part: PptxDeck.Part, ph: XmlNode, byIndex: Boolean): XmlNode? {
        val shapes = part.xml.descendants("p:sp").filter { isPlaceholder(it) }
        val idx = ph.attr("idx")
        if (byIndex && idx != null) shapes.firstOrNull { it.phNode()?.attr("idx") == idx }?.let { return it }
        val wanted = normalizeType(ph.attr("type"))
        shapes.firstOrNull { normalizeType(it.phNode()?.attr("type")) == wanted }?.let { return it }
        // Content placeholders (pictures, tables, charts) sit where the body goes.
        return if (wanted !in setOf("title", "dt", "ftr", "sldNum")) shapes.firstOrNull { normalizeType(it.phNode()?.attr("type")) == "body" } else null
    }

    private fun XmlNode.phNode() = path("p:nvSpPr", "p:nvPr", "p:ph")

    private fun normalizeType(type: String?): String = when (type) {
        null, "obj", "subTitle", "body" -> "body"
        "ctrTitle", "title" -> "title"
        else -> type
    }

    // ---- shapes ------------------------------------------------------------

    private fun renderShape(sp: XmlNode, slide: PptxDeck.Slide, canvas: Canvas) {
        val chain = placeholderChain(sp, slide)
        val frame = frameOf(sp.path("p:spPr", "a:xfrm"))
            ?: chain.firstNotNullOfOrNull { frameOf(it.path("p:spPr", "a:xfrm")) }
            ?: return
        val spPr = sp.child("p:spPr")
        val style = sp.child("p:style")
        val colors = slide.colors

        enter(canvas, frame)
        val geometry = spPr?.child("a:prstGeom")?.attr("prst") ?: if (spPr?.child("a:custGeom") != null) "custom" else "rect"
        val fillNode = spPr?.children?.firstOrNull { it.name == "a:solidFill" || it.name == "a:gradFill" || it.name == "a:noFill" || it.name == "a:blipFill" }
        val fill: Int? = when {
            fillNode?.name == "a:noFill" -> null
            fillNode?.name == "a:blipFill" -> null
            fillNode != null -> colors.fromFill(fillNode)
            style != null -> style.child("a:fillRef")?.takeIf { it.attr("idx") != "0" }?.let { colors.resolve(it.children.firstOrNull()) }
            else -> null
        }
        if (fillNode?.name == "a:blipFill") {
            fillNode.child("a:blip")?.attr("r:embed")?.let { bitmapFor(slide.part, it) }?.let { bmp ->
                canvas.drawBitmap(bmp, null, frame.rect, bitmapPaint)
            }
        }
        val line = spPr?.child("a:ln")
        val lineColor: Int? = when {
            line?.child("a:noFill") != null -> null
            line?.child("a:solidFill") != null -> colors.fromFill(line.child("a:solidFill"))
            line?.child("a:gradFill") != null -> colors.fromFill(line.child("a:gradFill"))
            style != null -> style.child("a:lnRef")?.takeIf { it.attr("idx") != "0" }?.let { colors.resolve(it.children.firstOrNull()) }
            else -> null
        }
        val lineWidth = pt(line?.attr("w")).takeIf { it > 0f } ?: 0.75f
        if (geometry != "custom") drawGeometry(canvas, geometry, frame.rect, fill, lineColor, lineWidth, spPr)

        sp.child("p:txBody")?.let { body ->
            val kind = normalizeType(sp.phNode()?.attr("type"))
            val isPlaceholder = sp.phNode() != null
            drawTextBody(
                body = body,
                rect = frame.rect,
                ownStyle = body.child("a:lstStyle"),
                inherited = textStyleChain(chain, slide, if (isPlaceholder) kind else "other"),
                // A styled shape's text colour comes from its font reference (white on a filled
                // box) before the master's text styles get a say.
                styleColor = style?.child("a:fontRef")?.children?.firstOrNull()?.let { colors.resolve(it) },
                colors = colors,
                defaultColor = colors.schemeColor("tx1") ?: Color.BLACK,
                canvas = canvas,
            )
        }
        canvas.restore()
    }

    private fun drawGeometry(
        canvas: Canvas, geometry: String, rect: RectF, fill: Int?, line: Int?, lineWidth: Float, spPr: XmlNode?,
    ) {
        val path = Path()
        when (geometry) {
            "ellipse" -> path.addOval(rect, Path.Direction.CW)
            "roundRect" -> {
                val adj = spPr?.descendants("a:gd")?.firstOrNull { it.attr("name") == "adj" }
                    ?.attr("fmla")?.removePrefix("val ")?.toFloatOrNull()?.div(100_000f) ?: 0.16667f
                val r = min(rect.width(), rect.height()) * adj
                path.addRoundRect(rect, r, r, Path.Direction.CW)
            }
            "triangle" -> { path.moveTo(rect.centerX(), rect.top); path.lineTo(rect.right, rect.bottom); path.lineTo(rect.left, rect.bottom); path.close() }
            "rtTriangle" -> { path.moveTo(rect.left, rect.top); path.lineTo(rect.right, rect.bottom); path.lineTo(rect.left, rect.bottom); path.close() }
            "diamond" -> { path.moveTo(rect.centerX(), rect.top); path.lineTo(rect.right, rect.centerY()); path.lineTo(rect.centerX(), rect.bottom); path.lineTo(rect.left, rect.centerY()); path.close() }
            "line", "straightConnector1" -> { path.moveTo(rect.left, rect.top); path.lineTo(rect.right, rect.bottom) }
            else -> path.addRect(rect, Path.Direction.CW)
        }
        if (fill != null && geometry != "line" && geometry != "straightConnector1") {
            canvas.drawPath(path, fillPaint.apply { color = fill })
        }
        if (line != null) {
            canvas.drawPath(path, strokePaint.apply { color = line; strokeWidth = lineWidth })
        }
    }

    private fun renderConnector(cxn: XmlNode, slide: PptxDeck.Slide, canvas: Canvas) {
        val frame = frameOf(cxn.path("p:spPr", "a:xfrm")) ?: return
        val line = cxn.path("p:spPr", "a:ln")
        val color = when {
            line?.child("a:noFill") != null -> return
            line?.child("a:solidFill") != null -> slide.colors.fromFill(line.child("a:solidFill"))
            else -> cxn.child("p:style")?.child("a:lnRef")?.let { slide.colors.resolve(it.children.firstOrNull()) }
        } ?: slide.colors.schemeColor("tx1") ?: Color.BLACK
        val width = pt(line?.attr("w")).takeIf { it > 0f } ?: 0.75f
        enter(canvas, frame)
        canvas.drawLine(frame.rect.left, frame.rect.top, frame.rect.right, frame.rect.bottom, strokePaint.apply { this.color = color; strokeWidth = width })
        canvas.restore()
    }

    // ---- pictures ----------------------------------------------------------

    private fun renderPicture(pic: XmlNode, slide: PptxDeck.Slide, canvas: Canvas) {
        val frame = frameOf(pic.path("p:spPr", "a:xfrm")) ?: run {
            val ph = pic.path("p:nvPicPr", "p:nvPr", "p:ph") ?: return
            listOfNotNull(slide.layout, slide.master).firstNotNullOfOrNull { part ->
                findPlaceholder(part, ph, byIndex = true)?.let { frameOf(it.path("p:spPr", "a:xfrm")) }
            } ?: return
        }
        val blipFill = pic.child("p:blipFill") ?: return
        val embed = blipFill.child("a:blip")?.attr("r:embed") ?: return
        val bitmap = bitmapFor(slide.part, embed) ?: return
        val src = blipFill.child("a:srcRect")?.let { crop ->
            fun pct(name: String) = crop.attr(name)?.toFloatOrNull()?.div(100_000f) ?: 0f
            Rect(
                (bitmap.width * pct("l")).roundToInt(), (bitmap.height * pct("t")).roundToInt(),
                (bitmap.width * (1f - pct("r"))).roundToInt(), (bitmap.height * (1f - pct("b"))).roundToInt(),
            ).takeIf { it.width() > 0 && it.height() > 0 }
        }
        enter(canvas, frame)
        canvas.drawBitmap(bitmap, src, frame.rect, bitmapPaint)
        pic.path("p:spPr", "a:ln")?.child("a:solidFill")?.let { ln ->
            slide.colors.fromFill(ln)?.let { c ->
                canvas.drawRect(frame.rect, strokePaint.apply { color = c; strokeWidth = pt(pic.path("p:spPr", "a:ln")?.attr("w")).takeIf { it > 0f } ?: 0.75f })
            }
        }
        canvas.restore()
    }

    private fun bitmapFor(part: PptxDeck.Part, relId: String): Bitmap? {
        val target = part.rels[relId]?.target ?: return null
        return images.getOrPut(target) {
            val ext = target.substringAfterLast('.', "").lowercase()
            if (ext in setOf("emf", "wmf", "svg")) return@getOrPut null
            val bytes = deck.bytes(target) ?: return@getOrPut null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@getOrPut null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_EDGE) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    // ---- graphic frames: tables and everything else ------------------------

    private fun renderGraphicFrame(frame: XmlNode, slide: PptxDeck.Slide, canvas: Canvas) {
        val rect = frameOf(frame.child("p:xfrm"))?.rect ?: run {
            val ph = frame.path("p:nvGraphicFramePr", "p:nvPr", "p:ph") ?: return
            listOfNotNull(slide.layout, slide.master).firstNotNullOfOrNull { part ->
                findPlaceholder(part, ph, byIndex = true)?.let { frameOf(it.path("p:spPr", "a:xfrm"))?.rect }
            } ?: return
        }
        val data = frame.path("a:graphic", "a:graphicData") ?: return
        val table = data.child("a:tbl")
        if (table != null) {
            renderTable(table, rect, slide, canvas)
            return
        }
        val uri = data.attr("uri").orEmpty()
        val label = when {
            uri.contains("chart", ignoreCase = true) -> "Chart"
            uri.contains("diagram", ignoreCase = true) -> "SmartArt"
            uri.contains("ole", ignoreCase = true) -> "Embedded object"
            else -> "Content"
        }
        canvas.drawRect(rect, fillPaint.apply { color = 0xFFF2F2F2.toInt() })
        canvas.drawRect(rect, strokePaint.apply { color = 0xFFBFBFBF.toInt(); strokeWidth = 0.75f })
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF808080.toInt(); textSize = 14f; textAlign = Paint.Align.CENTER }
        canvas.drawText(label, rect.centerX(), rect.centerY() + 5f, paint)
    }

    private fun renderTable(table: XmlNode, rect: RectF, slide: PptxDeck.Slide, canvas: Canvas) {
        val colors = slide.colors
        val props = table.child("a:tblPr")
        val headerRow = props?.attr("firstRow") == "1"
        val banded = props?.attr("bandRow") == "1"
        val accent = colors.schemeColor("accent1") ?: 0xFF4472C4.toInt()
        val widths = table.child("a:tblGrid")?.childrenNamed("a:gridCol")?.map { pt(it.attr("w")) } ?: return
        val rows = table.childrenNamed("a:tr")
        var y = rect.top
        val occupied = HashSet<Pair<Int, Int>>()
        rows.forEachIndexed { r, row ->
            val h = pt(row.attr("h"))
            var x = rect.left
            var c = 0
            row.childrenNamed("a:tc").forEach { cell ->
                while (occupied.contains(r to c)) { x += widths.getOrElse(c) { 0f }; c++ }
                val span = cell.attr("gridSpan")?.toIntOrNull() ?: 1
                val rowSpan = cell.attr("rowSpan")?.toIntOrNull() ?: 1
                val w = (0 until span).sumOf { widths.getOrElse(c + it) { 0f }.toDouble() }.toFloat()
                val cellHeight = (0 until rowSpan).sumOf { pt(rows.getOrNull(r + it)?.attr("h")).toDouble() }.toFloat()
                for (rr in 0 until rowSpan) for (cc in 0 until span) if (rr > 0 || cc > 0) occupied += (r + rr) to (c + cc)
                val merged = cell.attr("hMerge") == "1" || cell.attr("vMerge") == "1"
                if (!merged) {
                    val cellRect = RectF(x, y, x + w, y + cellHeight)
                    val tcPr = cell.child("a:tcPr")
                    val explicit = tcPr?.children?.firstOrNull { it.name == "a:solidFill" || it.name == "a:gradFill" }?.let { colors.fromFill(it) }
                    val fill = explicit ?: when {
                        headerRow && r == 0 -> accent
                        banded && (r - (if (headerRow) 1 else 0)) % 2 == 0 -> PptxColors.blend(accent, Color.WHITE, 0.80f)
                        banded -> PptxColors.blend(accent, Color.WHITE, 0.90f)
                        else -> null
                    }
                    fill?.let { canvas.drawRect(cellRect, fillPaint.apply { color = it }) }
                    canvas.drawRect(cellRect, strokePaint.apply { color = if (fill != null) Color.WHITE else 0xFFBFBFBF.toInt(); strokeWidth = 1f })
                    cell.child("a:txBody")?.let { body ->
                        val textColor = if (headerRow && r == 0 && explicit == null) Color.WHITE else colors.schemeColor("tx1") ?: Color.BLACK
                        drawTextBody(
                            body = body, rect = cellRect, ownStyle = null, inherited = emptyList(),
                            styleColor = null, colors = colors, defaultColor = textColor, canvas = canvas,
                            insets = floatArrayOf(pt(tcPr?.attr("marL")).takeIf { it > 0f } ?: 7.2f, pt(tcPr?.attr("marT")).takeIf { it > 0f } ?: 3.6f,
                                pt(tcPr?.attr("marR")).takeIf { it > 0f } ?: 7.2f, pt(tcPr?.attr("marB")).takeIf { it > 0f } ?: 3.6f),
                            anchorOverride = tcPr?.attr("anchor"),
                            defaultSize = 14f,
                            forceBold = headerRow && r == 0,
                        )
                    }
                }
                x += w
                c += span
            }
            y += h
        }
    }

    // ---- text --------------------------------------------------------------

    /**
     * The list styles a paragraph inherits from, most specific first: the shape's own, the layout
     * placeholder's, the master placeholder's, then the master's text style for this kind of shape.
     */
    private fun textStyleChain(chain: List<XmlNode>, slide: PptxDeck.Slide, kind: String): List<XmlNode> {
        val out = mutableListOf<XmlNode>()
        chain.forEach { ph -> ph.path("p:txBody", "a:lstStyle")?.let { out += it } }
        val styles = slide.master?.xml?.descendants("p:txStyles")?.firstOrNull()
        val master = when (kind) {
            "title" -> styles?.child("p:titleStyle")
            "body" -> styles?.child("p:bodyStyle")
            else -> styles?.child("p:otherStyle")
        }
        master?.let { out += it }
        if (kind != "other") styles?.child("p:otherStyle")?.let { out += it }
        return out
    }

    private class Para(
        val text: SpannableStringBuilder,
        val align: Layout.Alignment,
        val marginLeft: Float,
        val indent: Float,
        val bullet: String?,
        val bulletColor: Int,
        val bulletSize: Float,
        val lineMultiplier: Float,
        val spaceBefore: Float,
        val spaceAfter: Float,
        val emptyHeight: Float,
    )

    private fun drawTextBody(
        body: XmlNode,
        rect: RectF,
        ownStyle: XmlNode?,
        inherited: List<XmlNode>,
        styleColor: Int?,
        colors: PptxColors,
        defaultColor: Int,
        canvas: Canvas,
        insets: FloatArray? = null,
        anchorOverride: String? = null,
        defaultSize: Float = 18f,
        forceBold: Boolean = false,
    ) {
        val bodyPr = body.child("a:bodyPr")
        val lIns = insets?.get(0) ?: pt(bodyPr?.attr("lIns"), default = 7.2f)
        val tIns = insets?.get(1) ?: pt(bodyPr?.attr("tIns"), default = 3.6f)
        val rIns = insets?.get(2) ?: pt(bodyPr?.attr("rIns"), default = 7.2f)
        val bIns = insets?.get(3) ?: pt(bodyPr?.attr("bIns"), default = 3.6f)
        val anchor = anchorOverride ?: bodyPr?.attr("anchor") ?: "t"
        val autofit = bodyPr?.child("a:normAutofit")
        var scale = autofit?.attr("fontScale")?.toFloatOrNull()?.div(100_000f) ?: 1f
        val shrinkToFit = autofit != null && autofit.attr("fontScale") == null
        val width = max(1f, rect.width() - lIns - rIns)
        val available = max(1f, rect.height() - tIns - bIns)

        val paragraphs = body.childrenNamed("a:p")
        if (paragraphs.none { hasText(it) }) return

        var layouts: List<Pair<Para, StaticLayout?>>
        var total: Float
        var attempts = 0
        do {
            val paras = paragraphs.map { buildParagraph(it, ownStyle, inherited, styleColor, colors, defaultColor, scale, defaultSize, forceBold) }
            layouts = paras.map { para ->
                para to if (para.text.isEmpty()) null else layout(para, width)
            }
            total = layouts.sumOf { (para, layout) ->
                (para.spaceBefore + para.spaceAfter + (layout?.height?.div(TEXT_SCALE) ?: para.emptyHeight)).toDouble()
            }.toFloat()
            attempts++
            if (total <= available || !shrinkToFit || attempts > 6) break
            scale *= (available / total).coerceIn(0.5f, 0.95f)
        } while (true)

        val offset = when (anchor) {
            "ctr" -> (available - total) / 2f
            "b" -> available - total
            else -> 0f
        }.coerceAtLeast(0f)

        canvas.save()
        canvas.translate(rect.left + lIns, rect.top + tIns + offset)
        var y = 0f
        layouts.forEach { (para, layout) ->
            y += para.spaceBefore
            if (layout == null) {
                y += para.emptyHeight
            } else {
                canvas.save()
                canvas.translate(0f, y)
                canvas.scale(1f / TEXT_SCALE, 1f / TEXT_SCALE)
                para.bullet?.let { bullet ->
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = para.bulletColor
                        textSize = para.bulletSize * TEXT_SCALE
                    }
                    canvas.drawText(bullet, (para.marginLeft + para.indent).coerceAtLeast(0f) * TEXT_SCALE, layout.getLineBaseline(0).toFloat(), paint)
                }
                layout.draw(canvas)
                canvas.restore()
                y += layout.height / TEXT_SCALE
            }
            y += para.spaceAfter
        }
        canvas.restore()
    }

    private fun hasText(p: XmlNode): Boolean = p.children.any { it.name == "a:r" || it.name == "a:fld" || it.name == "a:br" }

    private fun layout(para: Para, width: Float): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f * TEXT_SCALE; color = Color.BLACK }
        val usable = max(1, ((width - para.marginLeft) * TEXT_SCALE).roundToInt() + (para.marginLeft * TEXT_SCALE).roundToInt())
        return StaticLayout.Builder.obtain(para.text, 0, para.text.length, paint, usable)
            .setAlignment(para.align)
            .setLineSpacing(0f, para.lineMultiplier)
            .setIncludePad(false)
            .build()
    }

    private fun buildParagraph(
        p: XmlNode, ownStyle: XmlNode?, inherited: List<XmlNode>, styleColor: Int?,
        colors: PptxColors, defaultColor: Int,
        scale: Float, defaultSize: Float, forceBold: Boolean,
    ): Para {
        val pPr = p.child("a:pPr")
        val level = pPr?.attr("lvl")?.toIntOrNull() ?: 0
        // Most specific first: the paragraph's own properties and the shape's own list style, then
        // each inherited list style's entry for this level.
        val own = listOfNotNull(pPr, ownStyle?.child("a:lvl${level + 1}pPr"))
        val rest = inherited.mapNotNull { it.child("a:lvl${level + 1}pPr") }
        val chain = own + rest

        fun <T> first(pick: (XmlNode) -> T?): T? = chain.firstNotNullOfOrNull(pick)
        val align = when (first { it.attr("algn") }) {
            "ctr" -> Layout.Alignment.ALIGN_CENTER
            "r" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val marginLeft = first { it.attr("marL")?.let(::pt) } ?: (if (level > 0) level * 36f else 0f)
        val indent = first { it.attr("indent")?.let(::pt) } ?: 0f
        val bullet = first { node ->
            when {
                node.child("a:buNone") != null -> ""
                node.child("a:buChar") != null -> node.child("a:buChar")!!.attr("char") ?: "•"
                node.child("a:buAutoNum") != null -> "•"
                else -> null
            }
        }?.takeIf { it.isNotEmpty() }
        val defRPr = first { it.child("a:defRPr") }
        val inheritedSize = chain.firstNotNullOfOrNull { it.child("a:defRPr")?.attr("sz")?.toFloatOrNull()?.div(100f) }
        val baseSize = (inheritedSize ?: defaultSize)
        val inheritedBold = chain.firstNotNullOfOrNull { it.child("a:defRPr")?.attr("b")?.let { b -> b == "1" } } ?: false
        val inheritedItalic = chain.firstNotNullOfOrNull { it.child("a:defRPr")?.attr("i")?.let { i -> i == "1" } } ?: false
        fun colorIn(nodes: List<XmlNode>) = nodes.firstNotNullOfOrNull { node ->
            node.child("a:defRPr")?.children?.firstOrNull { it.name == "a:solidFill" }?.let { colors.fromFill(it) }
        }
        val inheritedColor = colorIn(own) ?: styleColor ?: colorIn(rest) ?: defaultColor
        val lineMultiplier = first { node ->
            node.child("a:lnSpc")?.let { spc ->
                spc.child("a:spcPct")?.attr("val")?.toFloatOrNull()?.div(100_000f)
                    ?: spc.child("a:spcPts")?.attr("val")?.toFloatOrNull()?.div(100f)?.div(baseSize * 1.2f)
            }
        } ?: 1f
        fun spacing(name: String): Float = first { node ->
            node.child(name)?.let { spc ->
                spc.child("a:spcPts")?.attr("val")?.toFloatOrNull()?.div(100f)
                    ?: spc.child("a:spcPct")?.attr("val")?.toFloatOrNull()?.div(100_000f)?.times(baseSize)
            }
        } ?: 0f

        val text = SpannableStringBuilder()
        var firstRunSize = 0f
        var firstRunColor = inheritedColor
        p.children.forEach { node ->
            when (node.name) {
                "a:br" -> text.append("\n")
                "a:r", "a:fld" -> {
                    val content = node.child("a:t")?.text ?: return@forEach
                    if (content.isEmpty()) return@forEach
                    val rPr = node.child("a:rPr")
                    val size = (rPr?.attr("sz")?.toFloatOrNull()?.div(100f) ?: baseSize) * scale
                    val bold = forceBold || (rPr?.attr("b")?.let { it == "1" } ?: inheritedBold)
                    val italic = rPr?.attr("i")?.let { it == "1" } ?: inheritedItalic
                    val underline = rPr?.attr("u")?.let { it != "none" } ?: false
                    val color = rPr?.children?.firstOrNull { it.name == "a:solidFill" }?.let { colors.fromFill(it) } ?: inheritedColor
                    val face = rPr?.child("a:latin")?.attr("typeface")?.lowercase()
                    val family = when {
                        face == null -> null
                        face.contains("mono") || face.contains("courier") || face.contains("consolas") -> "monospace"
                        face.contains("serif") && !face.contains("sans") || face.contains("times") || face.contains("georgia") || face.contains("cambria") || face.contains("garamond") -> "serif"
                        else -> null
                    }
                    if (firstRunSize == 0f) { firstRunSize = size; firstRunColor = color }
                    val start = text.length
                    text.append(content)
                    val end = text.length
                    text.setSpan(AbsoluteSizeSpan((size * TEXT_SCALE).roundToInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val style = when {
                        bold && italic -> Typeface.BOLD_ITALIC
                        bold -> Typeface.BOLD
                        italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    if (style != Typeface.NORMAL) text.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (underline) text.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    family?.let { text.setSpan(TypefaceSpan(it), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                }
            }
        }
        if (text.isNotEmpty() && (marginLeft > 0f || bullet != null)) {
            val hang = (marginLeft * TEXT_SCALE).roundToInt()
            text.setSpan(LeadingMarginSpan.Standard(hang, hang), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val endSize = (p.child("a:endParaRPr")?.attr("sz")?.toFloatOrNull()?.div(100f) ?: baseSize) * scale
        return Para(
            text = text,
            align = align,
            marginLeft = marginLeft,
            indent = indent,
            bullet = bullet?.takeIf { text.isNotEmpty() },
            bulletColor = firstRunColor,
            bulletSize = if (firstRunSize > 0f) firstRunSize else endSize,
            lineMultiplier = lineMultiplier,
            spaceBefore = spacing("a:spcBef"),
            spaceAfter = spacing("a:spcAft"),
            emptyHeight = endSize * 1.2f * lineMultiplier,
        )
    }

    // ---- helpers -----------------------------------------------------------

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun pt(emu: Long): Float = emu / EMU_PER_POINT
    private fun pt(emu: String?, default: Float = 0f): Float = emu?.toLongOrNull()?.let { it / EMU_PER_POINT } ?: default

    companion object {
        const val EMU_PER_POINT = 12_700f
        /** Text is laid out at this multiple and drawn scaled down, for sub-point font sizes. */
        private const val TEXT_SCALE = 4f
        private const val MAX_IMAGE_EDGE = 2048
    }
}
