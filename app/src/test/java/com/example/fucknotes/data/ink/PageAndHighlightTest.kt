package com.example.fucknotes.data.ink

import com.example.fucknotes.data.model.InkTool
import org.junit.Assert.assertEquals
import org.junit.Test

class PageAndHighlightTest {
    @Test fun importedPageSizeSurvivesReopening() {
        val page = PageSpec(customWidth = 792f, customHeight = 612f)
        assertEquals(page, PageSpec.decode(page.encode()))
        assertEquals(1224f, NotebookLayout.topOf(2, page), 0f)
    }

    @Test fun malformedImportedSizeUsesSafeDefault() {
        listOf("PDF_NaN_100", "PDF_500_Infinity", "PDF_-1_500", "PDF_0_0").forEach {
            assertEquals(PageSpec.Default, PageSpec.decode(it))
        }
    }

    @Test fun legacyOpaqueHighlightsBecomeTransparentWithoutRepeatedFading() {
        val opaque = 0xFFFFDF43.toInt()
        val translucent = InkCodec.colorForTool(InkTool.HIGHLIGHTER, opaque)
        assertEquals(64, translucent ushr 24)
        assertEquals(opaque and 0xFFFFFF, translucent and 0xFFFFFF)
        assertEquals(translucent, InkCodec.colorForTool(InkTool.HIGHLIGHTER, translucent))
        assertEquals(opaque, InkCodec.colorForTool(InkTool.PEN, opaque))
    }
}
