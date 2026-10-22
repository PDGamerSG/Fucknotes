package com.example.fucknotes.ui.editor.ink

import org.junit.Assert.assertEquals
import org.junit.Test

class DarkReaderTest {
    @Test fun invertsRgbAndPreservesAlpha() {
        assertEquals(0x80443322.toInt(), darkReaderArgb(0x80BBCCDD.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), darkReaderArgb(0xFF000000.toInt()))
    }
}
