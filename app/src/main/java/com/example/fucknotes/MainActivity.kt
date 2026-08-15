package com.example.fucknotes

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                    FucknotesNavGraph(
                        navController = rememberNavController(),
                        openNoteId = pendingNoteId,
                        onOpenNoteHandled = { pendingNoteId = null },
                    )
                }
            }
        }
    }

    private fun Intent.noteIdExtra(): Long? =
        getLongExtra(EXTRA_OPEN_NOTE_ID, -1L).takeIf { it > 0 }

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"
    }
}
