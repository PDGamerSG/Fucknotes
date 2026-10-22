package com.example.fucknotes

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fucknotes.media.PdfImporter
import com.example.fucknotes.media.PptxImporter
import com.example.fucknotes.ui.home.DocumentKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DocumentOpenState(val progress: String? = null, val noteId: Long? = null, val error: String? = null)

/** Activity-owned import survives rotation and consumes VIEW/SEND intents once. */
class DocumentOpenViewModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(DocumentOpenState())
    val state = mutable.asStateFlow()
    private var initialHandled = false

    fun acceptInitial(intent: Intent?) {
        if (initialHandled) return
        initialHandled = true
        accept(intent)
    }

    fun accept(intent: Intent?) {
        if (intent == null) return
        val id = intent.getLongExtra(MainActivity.EXTRA_OPEN_NOTE_ID, -1L)
        if (id > 0) { mutable.value = DocumentOpenState(noteId = id); return }
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        val app = getApplication<Application>()
        val slides = when (DocumentKind.detect(app, uri, intent.type)) {
            DocumentKind.SLIDES -> true
            DocumentKind.PDF -> false
            null -> return
        }
        if (mutable.value.progress != null) return
        mutable.value = DocumentOpenState(progress = if (slides) "Opening presentation…" else "Opening PDF…")
        viewModelScope.launch {
            try {
                val repo = ServiceLocator.notes(app)
                val note = if (slides) {
                    PptxImporter(app, repo).import(uri, null) { mutable.value = DocumentOpenState(progress = it) }
                } else {
                    PdfImporter(app, repo).import(uri, null) { done, total ->
                        mutable.value = DocumentOpenState(progress = "Importing page $done of $total…")
                    }
                }
                mutable.value = DocumentOpenState(noteId = note)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                mutable.value = DocumentOpenState(error = "Cannot import: ${error.message}")
            }
        }
    }

    fun consumed() { mutable.value = DocumentOpenState() }
}
