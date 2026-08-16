package com.example.fucknotes

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.ui.nav.FucknotesNavGraph
import com.example.fucknotes.ui.theme.FucknotesTheme

/**
 * Single-activity host.
 *
 * It extends [FragmentActivity] because `BiometricPrompt` needs a fragment host to attach to when
 * a locked note is opened.
 */
class MainActivity : FragmentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* reminders degrade quietly */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        goFullscreen()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val settingsRepo = ServiceLocator.settings(this)

        setContent {
            val settings by settingsRepo.settings
                .collectAsStateWithLifecycle(initialValue = SettingsRepository.Settings())

            var pendingNoteId by remember { mutableStateOf(intent?.noteIdExtra()) }

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            FucknotesTheme(
                themeMode = settings.themeMode,
                accent = settings.accent,
                dynamicColor = settings.dynamicColor,
                fontScalePercent = settings.fontScalePercent,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // The surface itself stays full-bleed so its colour reaches every corner,
                    // including behind a notch; the UI inside keeps clear of the cutout. With the
                    // status bar hidden there is no bar left to hide the camera behind, and the
                    // window is laid out through it, so without this a top-bar icon would sit
                    // under the lens.
                    Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout)) {
                        FucknotesNavGraph(
                            navController = rememberNavController(),
                            openNoteId = pendingNoteId,
                            onOpenNoteHandled = { pendingNoteId = null },
                        )
                    }
                }
            }
        }
    }

    /**
     * The bars come back whenever the window loses focus — a permission dialog, the biometric
     * sheet, the recents switcher, the notification shade. Re-hiding on every focus gain is what
     * makes the fullscreen stick instead of lasting until the first interruption.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    /**
     * Hides the status and navigation bars for good.
     *
     * A page of handwriting wants the whole panel, and the navigation bar sits exactly where the
     * hand rests. `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` keeps them reachable: a swipe from the
     * edge floats them over the page, and they retreat on their own — so nothing is lost, and a
     * stray swipe cannot permanently resize the writing surface.
     */
    private fun goFullscreen() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun Intent.noteIdExtra(): Long? =
        getLongExtra(EXTRA_OPEN_NOTE_ID, -1L).takeIf { it > 0 }

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"
    }
}
