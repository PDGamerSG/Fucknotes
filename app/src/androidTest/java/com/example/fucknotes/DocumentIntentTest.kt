package com.example.fucknotes

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fucknotes.media.DocumentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app must be on the chooser for every shape a PDF or deck arrives in: the right type, the
 * legacy PowerPoint type, a download's octet-stream with only the extension to go by, and a share.
 */
@RunWith(AndroidJUnit4::class)
class DocumentIntentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pm = context.packageManager
    private val document = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FWeek%206.pptx")
    private val pdf = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FLecture.pdf")

    private fun offered(intent: Intent): Boolean =
        pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName == context.packageName }

    private fun view(uri: Uri, type: String?) = Intent(Intent.ACTION_VIEW).apply {
        if (type == null) data = uri else setDataAndType(uri, type)
    }

    @Test fun theAppIsOfferedForEveryDocumentShape() {
        assertTrue("pdf", offered(view(pdf, DocumentType.MIME_PDF)))
        assertTrue("pptx", offered(view(document, DocumentType.MIME_PPTX)))
        assertTrue("legacy ppt type", offered(view(document, DocumentType.MIME_PPT)))
        assertTrue("other ppt alias", offered(view(document, "application/mspowerpoint")))
        assertTrue("octet-stream pptx by name", offered(view(document, "application/octet-stream")))
        assertTrue("octet-stream pdf by name", offered(view(pdf, "application/octet-stream")))
        assertTrue("file pdf", offered(view(Uri.parse("file:///sdcard/Download/notes.pdf"), "application/octet-stream")))
        assertTrue("file ppt", offered(view(Uri.parse("file:///sdcard/Download/old.ppt"), "application/octet-stream")))
        assertTrue("share pdf", offered(Intent(Intent.ACTION_SEND).setType(DocumentType.MIME_PDF).putExtra(Intent.EXTRA_STREAM, pdf)))
        assertTrue("share legacy ppt", offered(Intent(Intent.ACTION_SEND).setType(DocumentType.MIME_PPT).putExtra(Intent.EXTRA_STREAM, document)))
    }

    @Test fun theAppStaysOutOfOtherFilesChoosers() {
        assertFalse(offered(view(Uri.parse("file:///sdcard/Download/photo.jpg"), "application/octet-stream")))
        assertFalse(offered(view(Uri.parse("file:///sdcard/Download/archive.zip"), "application/zip")))
        assertFalse(offered(Intent(Intent.ACTION_SEND).setType("image/png")))
    }
}
