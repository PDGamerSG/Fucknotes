package com.example.fucknotes.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotePositionsTest {
    @Test fun landsHalfwayBetweenNeighbours() {
        assertEquals(150L, NotePositions.between(100L, 200L))
        assertEquals(101L, NotePositions.between(100L, 102L))
    }

    @Test fun stepsPastTheOnlyNeighbour() {
        assertEquals(100L + NotePositions.STEP, NotePositions.between(100L, null))
        assertEquals(100L - NotePositions.STEP, NotePositions.between(null, 100L))
    }

    @Test fun aShelfOfOneSitsAtZero() {
        assertEquals(0L, NotePositions.between(null, null))
    }

    @Test fun adjacentNeighboursAskForARenumber() {
        assertNull(NotePositions.between(100L, 101L))
        assertNull(NotePositions.between(100L, 100L))
    }
}
