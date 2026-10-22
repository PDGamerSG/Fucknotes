package com.example.fucknotes.ui.settings

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.model.PenButton
import com.example.fucknotes.data.model.PenButtonAction
import com.example.fucknotes.data.model.ThemeMode
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.input.PenInputBus
import com.example.fucknotes.ui.common.ImmersiveDialogWindow
import com.example.fucknotes.ui.home.CreateAction
import com.example.fucknotes.ui.components.OptionListDialog
import com.example.fucknotes.ui.theme.Radii
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
    fun setDarkReader(v: Boolean) = viewModelScope.launch { settingsRepo.setDarkReader(v) }
    fun setDefaultPaper(v: PaperStyle) = viewModelScope.launch { settingsRepo.setDefaultPaper(v) }
    fun setFontScale(v: Int) = viewModelScope.launch { settingsRepo.setFontScale(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settingsRepo.setKeepScreenOn(v) }
    fun setFingerInputMode(v: FingerInputMode) = viewModelScope.launch { settingsRepo.setFingerInputMode(v) }
    fun setConfirmDelete(v: Boolean) = viewModelScope.launch { settingsRepo.setConfirmDelete(v) }
    fun setAutoBackup(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoBackup(v) }
    fun setPenPrimaryAction(v: PenButtonAction) = viewModelScope.launch { settingsRepo.setPenPrimaryAction(v) }
    fun setPenSecondaryAction(v: PenButtonAction) = viewModelScope.launch { settingsRepo.setPenSecondaryAction(v) }
    fun setPenTertiaryAction(v: PenButtonAction) = viewModelScope.launch { settingsRepo.setPenTertiaryAction(v) }



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
    val lastPenInput by PenInputBus.lastDebugEvent.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showTheme by remember { mutableStateOf(false) }
    var showAccent by remember { mutableStateOf(false) }
    var showPaper by remember { mutableStateOf(false) }
    var showFingerModes by remember { mutableStateOf(false) }
    var showPenControls by remember { mutableStateOf(false) }

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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(vertical = 8.dp)) {
                SettingsGroup("Display and theme") {
                    SettingRow(
                        title = "Theme",
                        subtitle = settings.themeMode.name.lowercase().replaceFirstChar(Char::uppercase),
                        onClick = { showTheme = true },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        RowDivider()
                        SwitchRow(
                            title = "Use wallpaper colours",
                            subtitle = "Follow the system palette instead of a preset",
                            checked = settings.dynamicColor,
                            onChange = viewModel::setDynamicColor,
                        )
                    }
                    RowDivider()
                    SwitchRow(
                        title = "Dark mode for pages",
                        subtitle = "Night colours for imported documents and for notes that have not chosen. Each notebook picks light or dark when it is made; change it from its ⋯ menu.",
                        checked = settings.darkReader,
                        onChange = viewModel::setDarkReader,
                    )
                    RowDivider()
                    SettingRow(
                        title = "Theme colour",
                        subtitle = settings.accent.label,
                        enabled = !settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                        onClick = { showAccent = true },
                    )
                    RowDivider()
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("Text size — ${settings.fontScalePercent}%", style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value = settings.fontScalePercent.toFloat(),
                            onValueChange = { viewModel.setFontScale(it.toInt()) },
                            valueRange = 80f..160f,
                            steps = 7,
                        )
                    }
                }

                SettingsGroup("Default settings for notes") {
                    SettingRow(
                        title = "Default paper style",
                        subtitle = settings.defaultPaper.name.lowercase().replaceFirstChar(Char::uppercase),
                        onClick = { showPaper = true },
                    )
                    RowDivider()
                    SettingRow(
                        title = "Finger input",
                        subtitle = "${settings.fingerInputMode.label}: ${settings.fingerInputMode.description}",
                        onClick = { showFingerModes = true },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Keep screen on while editing",
                        subtitle = null,
                        checked = settings.keepScreenOn,
                        onChange = viewModel::setKeepScreenOn,
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Confirm before deleting",
                        subtitle = null,
                        checked = settings.confirmDelete,
                        onChange = viewModel::setConfirmDelete,
                    )
                }

                SettingsGroup("Xiaomi Pen buttons") {
                    Text(
                        "Assign shortcuts to the pen buttons. The primary button is the one most Xiaomi Pens expose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp, 14.dp, 20.dp, 4.dp),
                    )
                    SettingRow(
                        title = "Customize pen buttons",
                        subtitle = "Primary: ${settings.penPrimaryAction.label}  ·  Secondary: ${settings.penSecondaryAction.label}  ·  Tertiary: ${settings.penTertiaryAction.label}",
                        onClick = { showPenControls = true },
                    )
                    RowDivider()
                    Text(
                        text = lastPenInput?.let { "Last raw input: $it" }
                            ?: "Press a Xiaomi pen button while this screen is open to identify its input.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 14.dp),
                    )
                }

                SettingsGroup("Backup") {
                    SettingRow(
                        title = "Backup now",
                        subtitle = settings.lastBackupAt.ifBlank { "No backup yet" }
                            .let { if (settings.lastBackupAt.isBlank()) it else "Last backup: $it" },
                        enabled = !busy,
                        onClick = { createBackup.launch(BackupManager.suggestedFileName()) },
                    )
                    RowDivider()
                    SettingRow(
                        title = "Restore from backup",
                        subtitle = "Merges the backup into the notes already on this device",
                        enabled = !busy,
                        onClick = { openBackup.launch(arrayOf(BackupManager.MIME, "application/octet-stream")) },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Auto backup reminder",
                        subtitle = "Prompt to back up when the app starts",
                        checked = settings.autoBackup,
                        onChange = viewModel::setAutoBackup,
                    )
                }

                SettingsGroup("Opening documents") {
                    DocumentHandlerRow("PDF files", CreateAction.MIME_PDF)
                    RowDivider()
                    DocumentHandlerRow("PowerPoint files", CreateAction.MIME_PPTX)
                    RowDivider()
                    DocumentHandlerRow("Older PowerPoint files (.ppt)", CreateAction.MIME_PPT)
                }

                SettingsGroup("About") {
                    SettingRow(
                        title = "Fucknotes",
                        subtitle = "Version ${versionName(context)} · local-only notes, no account required",
                        onClick = {},
                    )
                    Text(
                        "Notes, folders, tags, ink and backups all live on this device. " +
                            "Nothing is uploaded anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp, 0.dp, 20.dp, 16.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
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

    if (showFingerModes) {
        OptionListDialog(
            title = "Finger input",
            options = FingerInputMode.entries.map { it.label },
            selectedIndex = FingerInputMode.entries.indexOf(settings.fingerInputMode),
            onDismiss = { showFingerModes = false },
            onPick = { viewModel.setFingerInputMode(FingerInputMode.entries[it]); showFingerModes = false },
        )
    }

    if (showPenControls) {
        PenButtonControlsSheet(
            settings = settings,
            onPrimary = viewModel::setPenPrimaryAction,
            onSecondary = viewModel::setPenSecondaryAction,
            onTertiary = viewModel::setPenTertiaryAction,
            onDismiss = { showPenControls = false },
        )
    }


}

/**
 * One titled card of related rows.
 *
 * Settings used to be a single flat list across the whole width, which on a tablet put a switch
 * a hand-span from the label it belonged to. Grouping into cards and capping the width keeps each
 * control next to its words.
 */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(Radii.card),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PenButtonControlsSheet(
    settings: SettingsRepository.Settings,
    onPrimary: (PenButtonAction) -> Unit,
    onSecondary: (PenButtonAction) -> Unit,
    onTertiary: (PenButtonAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<PenButton?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Xiaomi Pen", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Use the pen buttons for quick actions while writing. Changes apply immediately in every ink note.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PenButtonCard("1", "Primary button", settings.penPrimaryAction) { editing = PenButton.PRIMARY }
            PenButtonCard("2", "Secondary button", settings.penSecondaryAction) { editing = PenButton.SECONDARY }
            PenButtonCard("3", "Tertiary button", settings.penTertiaryAction) { editing = PenButton.TERTIARY }
            Text(
                "If your pen has one button, use Primary. Some Xiaomi firmware reports the same control as a stylus button event or a key event; both are supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { button ->
        val selected = when (button) {
            PenButton.PRIMARY -> settings.penPrimaryAction
            PenButton.SECONDARY -> settings.penSecondaryAction
            PenButton.TERTIARY -> settings.penTertiaryAction
        }
        OptionListDialog(
            title = when (button) {
                PenButton.PRIMARY -> "Primary pen button"
                PenButton.SECONDARY -> "Secondary pen button"
                PenButton.TERTIARY -> "Tertiary pen button"
            },
            options = PenButtonAction.entries.map { it.label },
            selectedIndex = PenButtonAction.entries.indexOf(selected),
            onDismiss = { editing = null },
            onPick = { index ->
                val action = PenButtonAction.entries[index]
                when (button) {
                    PenButton.PRIMARY -> onPrimary(action)
                    PenButton.SECONDARY -> onSecondary(action)
                    PenButton.TERTIARY -> onTertiary(action)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun PenButtonCard(
    number: String,
    title: String,
    action: PenButtonAction,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(number, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(action.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
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
            .padding(20.dp, 14.dp),
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
            .padding(20.dp, 14.dp),
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
