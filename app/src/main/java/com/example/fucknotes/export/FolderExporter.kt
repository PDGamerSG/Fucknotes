package com.example.fucknotes.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.ui.home.HomeExportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes through Android's document picker; no broad storage permission is needed. */
/** [editable] attaches each note's data to its PDF, the way the editor's export and Drive do. */
class FolderExporter(private val context: Context, private val exporter: NoteExporter, private val editable: Boolean = true) {
    suspend fun save(
        notes: List<NoteEntity>, folders: List<FolderEntity>, rootId: Long?, title: String,
        mode: HomeExportMode, destination: Uri, onProgress: (Int, Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(notes.isNotEmpty()) { "There are no notes to export" }
        val paths = exportPathsFor(notes, folders, rootId)
        when (mode) {
            HomeExportMode.COMBINED -> {
                val file = exporter.exportAll(notes, ExportFormat.PDF)
                try { copyToDocument(file, destination) } finally { file.delete() }
            }
            HomeExportMode.ZIP -> {
                val output = context.contentResolver.openOutputStream(destination, "wt")
                    ?: error("Cannot write to this location")
                output.use { raw ->
                    ZipOutputStream(raw.buffered()).use { zip ->
                        notes.forEachIndexed { index, note ->
                            currentCoroutineContext().ensureActive()
                            val file = exporter.export(note, ExportFormat.PDF, editable = editable)
                            try {
                                zip.putNextEntry(ZipEntry(paths.getValue(note.id).joinToString("/")))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            } finally { file.delete() }
                            onProgress(index + 1, notes.size)
                        }
                    }
                }
            }
            HomeExportMode.SEPARATE -> {
                val parent = DocumentFile.fromTreeUri(context, destination)
                    ?: error("Cannot open this folder")
                require(parent.canWrite()) { "This folder is read-only" }
                val used = parent.listFiles().mapNotNull { it.name?.lowercase(java.util.Locale.ROOT) }.toMutableSet()
                val output = parent.createDirectory(ExportNames.unique(title, used))
                    ?: error("Cannot create an export folder")
                try {
                    val directories = mutableMapOf<List<String>, DocumentFile>(emptyList<String>() to output)
                    notes.forEachIndexed { index, note ->
                        currentCoroutineContext().ensureActive()
                        val parts = paths.getValue(note.id)
                        var dir = output
                        parts.dropLast(1).forEachIndexed { depth, part ->
                            dir = directories.getOrPut(parts.take(depth + 1)) {
                                dir.createDirectory(part) ?: error("Cannot create $part")
                            }
                        }
                        val file = exporter.export(note, ExportFormat.PDF, editable = editable)
                        try {
                            val target = dir.createFile("application/pdf", parts.last())
                                ?: error("Cannot save ${parts.last()}")
                            copyToDocument(file, target.uri)
                        } finally { file.delete() }
                        onProgress(index + 1, notes.size)
                    }
                } catch (error: Exception) {
                    // Only this newly created export directory is eligible for cleanup.
                    output.delete()
                    throw error
                }
            }
        }
    }

    private fun copyToDocument(file: File, target: Uri) {
        context.contentResolver.openOutputStream(target, "wt")?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        } ?: error("Cannot write to this location")
    }

}
