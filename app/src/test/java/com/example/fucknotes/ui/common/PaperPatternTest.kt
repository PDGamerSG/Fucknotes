package com.example.fucknotes.ui.common

import androidx.compose.ui.graphics.PointMode
import com.example.fucknotes.data.model.PaperStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperPatternTest {
    @Test fun plainPaperHasNoPattern() {
        assertNull(paperPatternPoints(PaperStyle.PLAIN, 10f, 100f, 100f))
        assertNull(paperPatternPoints(PaperStyle.DOTTED, 0.5f, 100f, 100f))
    }

    @Test fun dotsFillTheSheetOnTheGrid() {
        val (mode, points, _) = paperPatternPoints(PaperStyle.DOTTED, 10f, 100f, 50f)!!
        assertEquals(PointMode.Points, mode)
        // x in 10..90 (9 columns), y in 10..40 (4 rows)
        assertEquals(36, points.size)
        assertTrue(points.all { it.x in 10f..90f && it.y in 10f..40f })
    }

    @Test fun rulesAreLinePairsAndGridsAddColumns() {
        val (mode, lined, _) = paperPatternPoints(PaperStyle.LINED, 10f, 100f, 50f)!!
        assertEquals(PointMode.Lines, mode)
        assertEquals(4 * 2, lined.size)
        val (_, grid, _) = paperPatternPoints(PaperStyle.GRID, 10f, 100f, 50f)!!
        assertEquals(4 * 2 + 9 * 2, grid.size)
    }
}
