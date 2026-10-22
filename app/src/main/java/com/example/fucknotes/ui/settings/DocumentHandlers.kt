package com.example.fucknotes.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Who opens a kind of document right now.
 *
 * Android remembers an "always" choice per type. Once another reader holds it, no chooser is
 * shown and this app never appears, however many filters it declares — so Settings says who has
 * the file and leads to the one place the choice can be cleared, that app's own info page.
 */
sealed class DocumentHandler {
    /** This app is the default. */
    object Self : DocumentHandler()

    /** No default: Android asks, and this app is on the list. */
    object Asks : DocumentHandler()

    /** Another app took the type as its default. */
    data class Other(val packageName: String, val label: String) : DocumentHandler()

    companion object {
        private val resolverPackages = setOf("android", "com.android.intentresolver")

        fun current(context: Context, mime: String): DocumentHandler {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2Fprobe"),
                mime,
            )
            val pm = context.packageManager
            val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return Asks
            val info = resolved.activityInfo ?: return Asks
            val name = info.name.orEmpty()
            return when {
                info.packageName == context.packageName -> Self
                info.packageName in resolverPackages || name.endsWith("ResolverActivity") ||
                    name.endsWith("ChooserActivity") -> Asks
                else -> Other(info.packageName, resolved.loadLabel(pm).toString())
            }
        }

        /** The other app's info page, where "Open by default → Clear defaults" lives. */
        fun appInfoIntent(packageName: String): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** One row per document kind: who opens it, and a way to take it back when another app does. */
@Composable
fun DocumentHandlerRow(title: String, mime: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var handler by remember(mime) { mutableStateOf(DocumentHandler.current(context, mime)) }
    // The user comes back from the other app's info page; look again on every resume.
    DisposableEffect(lifecycle, mime) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) handler = DocumentHandler.current(context, mime)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val current = handler
    Row(
        Modifier.fillMaxWidth().padding(20.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (current) {
                    DocumentHandler.Self -> "Open in Fucknotes."
                    DocumentHandler.Asks -> "Android asks which app to use. Fucknotes is on the list."
                    is DocumentHandler.Other ->
                        "Always open in ${current.label}. Clear its defaults under “Open by default” and Fucknotes is offered next time."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (current is DocumentHandler.Other) {
            TextButton(onClick = {
                runCatching { context.startActivity(DocumentHandler.appInfoIntent(current.packageName)) }
            }) { Text("Change") }
        }
    }
}
