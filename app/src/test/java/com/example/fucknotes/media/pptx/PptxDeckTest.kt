package com.example.fucknotes.media.pptx

import org.junit.Assert.assertEquals
import org.junit.Test

class PptxDeckTest {
    @Test fun relationshipTargetsResolveAgainstThePartDirectory() {
        assertEquals("ppt/slideLayouts/slideLayout1.xml", PptxDeck.resolve("ppt/slides", "../slideLayouts/slideLayout1.xml"))
        assertEquals("ppt/media/image1.png", PptxDeck.resolve("ppt/slides", "../media/image1.png"))
        assertEquals("ppt/slides/slide1.xml", PptxDeck.resolve("ppt", "slides/slide1.xml"))
        assertEquals("ppt/media/x.png", PptxDeck.resolve("ppt/slides", "/ppt/media/x.png"))
        assertEquals("ppt/theme/theme1.xml", PptxDeck.resolve("ppt/slideMasters", "./../theme/theme1.xml"))
    }
}
