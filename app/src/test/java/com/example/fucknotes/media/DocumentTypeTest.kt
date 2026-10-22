package com.example.fucknotes.media

import com.example.fucknotes.media.DocumentType.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The type a sender declares is believed when it is specific, and the file name decides otherwise. */
class DocumentTypeTest {
    @Test fun specificTypesWin() {
        assertEquals(Kind.PDF, DocumentType.kindOf("application/pdf", "anything.bin"))
        assertEquals(Kind.PDF, DocumentType.kindOf("application/x-pdf", null))
        assertEquals(Kind.SLIDES, DocumentType.kindOf(DocumentType.MIME_PPTX, "deck"))
        assertEquals(Kind.SLIDES, DocumentType.kindOf("application/vnd.ms-powerpoint", "deck.pptx"))
        assertEquals(Kind.SLIDES, DocumentType.kindOf("application/mspowerpoint", null))
        assertEquals(Kind.SLIDES, DocumentType.kindOf("APPLICATION/VND.MS-POWERPOINT; charset=binary", null))
    }

    @Test fun theNameDecidesWhenTheTypeIsVagueOrMissing() {
        assertEquals(Kind.PDF, DocumentType.kindOf("application/octet-stream", "Lecture 3.PDF"))
        assertEquals(Kind.SLIDES, DocumentType.kindOf("application/octet-stream", "week6.pptx"))
        assertEquals(Kind.SLIDES, DocumentType.kindOf(null, "old-deck.ppt"))
        assertEquals(Kind.PDF, DocumentType.kindOf("*/*", "/document/primary:Download/notes.pdf"))
    }

    @Test fun anythingElseIsNotADocument() {
        assertNull(DocumentType.kindOf("image/jpeg", "photo.jpg"))
        assertNull(DocumentType.kindOf("application/octet-stream", "archive.zip"))
        assertNull(DocumentType.kindOf(null, null))
        assertNull(DocumentType.kindOf("text/plain", "pdf-notes.txt"))
    }
}
