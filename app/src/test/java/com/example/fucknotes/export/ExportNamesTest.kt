package com.example.fucknotes.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportNamesTest {
    @Test fun keepsReadableUnicodeNames() {
        assertEquals("Lecture 1 — résumé (final)", ExportNames.safeBase("Lecture 1 — résumé (final)"))
    }

    @Test fun removesPathSeparatorsAndEmptyNames() {
        assertEquals("_folder_notes", ExportNames.safeBase("../folder/notes"))
        assertEquals("Untitled", ExportNames.safeBase(" ... "))
    }

    @Test fun disambiguatesCaseInsensitiveCollisionsOnlyWithinExport() {
        val used = mutableSetOf<String>()
        assertEquals("Notes.pdf", ExportNames.unique("Notes", used, ".pdf"))
        assertEquals("notes (2).pdf", ExportNames.unique("notes", used, ".pdf"))
        assertEquals("Notes.pdf", ExportNames.unique("Notes", mutableSetOf(), ".pdf"))
    }
}
