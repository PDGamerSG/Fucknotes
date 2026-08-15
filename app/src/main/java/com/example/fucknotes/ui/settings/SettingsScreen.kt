package com.example.fucknotes.ui.settings

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fucknotes.ServiceLocator
import com.example.fucknotes.data.backup.BackupManager
import com.example.fucknotes.data.model.AccentPreset
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.model.ThemeMode
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.ui.components.ConfirmDialog
import com.example.fucknotes.ui.components.OptionListDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = ServiceLocator.settings(app)
    private val notesRepo = ServiceLocator.notes(app)
    private val backup = ServiceLocator.backup(app)

    val settings: StateFlow<SettingsRepository.Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.Settings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(v) }
    fun setAccent(v: AccentPreset) = viewModelScope.launch { settingsRepo.setAccent(v) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { settingsRepo.setDynamicColor(v) }
    fun setDefaultPaper(v: PaperStyle) = viewModelScope.launch { settingsRepo.setDefaultPaper(v) }
    fun setFontScale(v: Int) = viewModelScope.launch { settingsRepo.setFontScale(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settingsRepo.setKeepScreenOn(v) }
    fun setFingerDrawing(v: Boolean) = viewModelScope.launch { settingsRepo.setFingerDrawing(v) }
    fun setConfirmDelete(v: Boolean) = viewModelScope.launch { settingsRepo.setConfirmDelete(v) }
    fun setAutoBackup(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoBackup(v) }
    fun setBiometricUnlock(v: Boolean) = viewModelScope.launch { settingsRepo.setBiometricUnlock(v) }

    fun unlockAllNotes() = viewModelScope.launch {
        notesRepo.unlockAll()
        _message.value = "All locked notes have been unlocked"
    }

    fun exportBackup(target: Uri) = viewModelScope.launch {
        _busy.value = true
        runCatching { backup.exportTo(target) }
            .onSuccess { count ->
                val stamp = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
                settingsRepo.setLastBackupAt(stamp)
                _message.value = "Backup completed — $count notes saved"
            }
            .onFailure { _message.value = "Backup failed: ${it.message}" }
        _busy.value = false
    }

    fun importBackup(source: Uri) = viewModelScope.launch {
        _busy.value = true
        runCatching { backup.importFrom(source) }
            .onSuccess { _message.value = "Restored $it notes" }
            .onFailure { _message.value = "Restore failed: ${it.message}" }
        _busy.value = false
    }

    fun consumeMessage() {
        _message.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showTheme by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var showPaper by remember { mutableStateOf(false) }
    var confirmUnlockAll by remember { mutableStateOf(false) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME)
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importBackup) }

    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Display and theme")

            SettingRow(
                title = "Theme",
                subtitle = settings.themeMode.name.lowercase().replaceFirstChar(Char::uppercase),
                onClick = { showTheme = true },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow(
                    title = "Use wallpaper colours",
                    subtitle = "Follow the system palette instead of a preset",
                    checked = settings.dynamicColor,
                    onChange = viewModel::setDynamicColor,
                )
            }
            SettingRow(
                title = "Theme colour",
                subtitle = settings.accent.label,
                enabled = !settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                onClick = { showAccent = true },
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Text size — ${settings.fontScalePercent}%", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = settings.fontScalePercent.toFloat(),
                    onValueChange = { viewModel.setFontScale(it.toInt()) },
                    valueRange = 80f..160f,
                    steps = 7,
                )
            }

            HorizontalDivider()
            SectionHeader("Default settings for notes")

            SettingRow(
                title = "Default paper style",
                subtitle = settings.defaultPaper.name.lowercase().replaceFirstChar(Char::uppercase),
                onClick = { showPaper = true },
            )
            SwitchRow(
                title = "Draw with finger",
                subtitle = "Off: only a stylus writes, and a finger scrolls the page",
                checked = settings.fingerDrawing,
                onChange = viewModel::setFingerDrawing,
            )
            SwitchRow(
                title = "Keep screen on while editing",
                subtitle = null,
                checked = settings.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )
            SwitchRow(
                title = "Confirm before deleting",
                subtitle = null,
                checked = settings.confirmDelete,
                onChange = viewModel::setConfirmDelete,
            )

            HorizontalDivider()
            SectionHeader("Note lock")

            SwitchRow(
                title = "Unlock with biometrics",
                subtitle = "Fingerprint, face or device passcode",
                checked = settings.biometricUnlock,
                onChange = viewModel::setBiometricUnlock,
            )
            SettingRow(
                title = "Unlock all notes",
                subtitle = "Removes the lock from every locked note",
                onClick = { confirmUnlockAll = true },
            )

            HorizontalDivider()
            SectionHeader("Backup")

            SettingRow(
                title = "Backup now",
                subtitle = settings.lastBackupAt.ifBlank { "No backup yet" }
                    .let { if (settings.lastBackupAt.isBlank()) it else "Last backup: $it" },
                enabled = !busy,
                onClick = { createBackup.launch(BackupManager.suggestedFileName()) },
            )
            SettingRow(
                title = "Restore from backup",
                subtitle = "Merges the backup into the notes already on this device",
                enabled = !busy,
                onClick = { openBackup.launch(arrayOf(BackupManager.MIME, "application/octet-stream")) },
            )
            SwitchRow(
                title = "Auto backup reminder",
                subtitle = "Prompt to back up when the app starts",
                checked = settings.autoBackup,
                onChange = viewModel::setAutoBackup,
            )

            HorizontalDivider()
            SectionHeader("About")

            SettingRow(
                title = "Fucknotes",
                subtitle = "Version ${versionName(context)} · local-only notes, no account required",
                onClick = {},
            )
            Text(
                "Notes, folders, tags, ink, reminders and backups all live on this device. " +
                    "Nothing is uploaded anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp, 4.dp, 20.dp, 32.dp),
            )
        }
    }

    if (showTheme) {
        OptionListDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
            onDismiss = { showTheme = false },
            onPick = { viewModel.setThemeMode(ThemeMode.entries[it]); showTheme = false },
        )
    }

    if (showAccent) {
        OptionListDialog(
            title = "Theme colour",
            options = AccentPreset.entries.map { it.label },
            selectedIndex = AccentPreset.entries.indexOf(settings.accent),
            onDismiss = { showAccent = false },
            onPick = { viewModel.setAccent(AccentPreset.entries[it]); showAccent = false },
        )
    }

    if (showPaper) {
        OptionListDialog(
            title = "Default paper style",
            options = PaperStyle.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            selectedIndex = PaperStyle.entries.indexOf(settings.defaultPaper),
            onDismiss = { showPaper = false },
            onPick = { viewModel.setDefaultPaper(PaperStyle.entries[it]); showPaper = false },
        )
    }

    if (confirmUnlockAll) {
        ConfirmDialog(
            title = "Unlock all notes?",
            message = "All locked notes will be unlocked.",
            confirmText = "Unlock all",
            onDismiss = { confirmUnlockAll = false },
            onConfirm = {
                confirmUnlockAll = false
                viewModel.unlockAllNotes()
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 6.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(20.dp, 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(20.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun versionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
}.getOrDefault("1.0")
