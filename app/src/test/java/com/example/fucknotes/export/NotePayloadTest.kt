package com.example.fucknotes.export

import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.media.NoteRestorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotePayloadTest {
    private val payload = NotePayload(
        installId = "install-a", sourceNoteId = 7, title = "Week 6", type = "NOTEBOOK",
        drawingJson = "{\"strokes\":[]}", paperStyle = "DOTTED", createdAt = 100, updatedAt = 200,
        tags = listOf("dsa"), folderPath = listOf("DSA", "Graphs"),
        images = listOf(NotePayload.ImageRef("image-0001.jpg", pageIndex = 0, x = 1f, y = 2f, width = 3f, height = 4f)),
    )

    @Test fun roundTripsThroughJson() {
        assertEquals(payload, NotePayload.decode(payload.encode()))
    }

    @Test fun refusesWhatIsNotANote() {
        assertNull(NotePayload.decode("{}"))
        assertNull(NotePayload.decode("not json"))
        assertNull(NotePayload.decode(payload.copy(app = "other").encode()))
        assertNull(NotePayload.decode(payload.copy(format = 99).encode()))
    }

    @Test fun aFileComingHomeFindsItsNoteAndOnlyANewerFileOverwritesIt() {
        val local = NoteEntity(id = 7, title = "Week 6", updatedAt = 150)
        assertEquals(NoteRestorer.Decision.UPDATE_EXISTING, NoteRestorer.decide(payload, "install-a", local))
        assertEquals(NoteRestorer.Decision.KEEP_EXISTING, NoteRestorer.decide(payload.copy(updatedAt = 150), "install-a", local))
        assertEquals(NoteRestorer.Decision.KEEP_EXISTING, NoteRestorer.decide(payload.copy(updatedAt = 100), "install-a", local))
        assertEquals(NoteRestorer.Decision.CREATE, NoteRestorer.decide(payload, "install-b", local))
        assertEquals(NoteRestorer.Decision.CREATE, NoteRestorer.decide(payload, "install-a", null))
        assertEquals(NoteRestorer.Decision.CREATE, NoteRestorer.decide(payload, "install-a", local.copy(deletedAt = 1)))
    }

    @Test fun thePayloadPartIsFoundByName() {
        val parts = listOf(
            EditablePdf.Part("image-0001.jpg", ByteArray(3)),
            EditablePdf.Part(EditablePdf.PART_NOTE, payload.encode().toByteArray()),
        )
        assertEquals(payload, NoteRestorer.payloadOf(parts))
        assertNull(NoteRestorer.payloadOf(parts.take(1)))
    }
}
