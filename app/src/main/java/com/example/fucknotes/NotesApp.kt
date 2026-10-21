package com.example.fucknotes

import android.app.Application
import android.content.Context
import com.example.fucknotes.data.backup.BackupManager
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container. The app is small enough that a service locator beats pulling in a
 * DI framework, and it keeps the KSP round short.
 */
object ServiceLocator {

    @Volatile private var repo: NotesRepository? = null
    @Volatile private var settings: SettingsRepository? = null
    @Volatile private var backup: BackupManager? = null
    private val persistence = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun notes(context: Context): NotesRepository = repo ?: synchronized(this) {
        repo ?: NotesRepository(AppDatabase.get(context)).also { repo = it }
    }

    fun settings(context: Context): SettingsRepository = settings ?: synchronized(this) {
        settings ?: SettingsRepository(context.applicationContext).also { settings = it }
    }

    fun backup(context: Context): BackupManager = backup ?: synchronized(this) {
        backup ?: BackupManager(context.applicationContext, AppDatabase.get(context)).also { backup = it }
    }

    /** Short writes that must survive a screen being popped, such as adding a notebook sheet. */
    fun persistenceScope(): CoroutineScope = persistence
}

class NotesApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // Trash keeps notes for 30 days, then drops them for good.
            ServiceLocator.notes(this@NotesApp).purgeOldTrash()
        }
    }
}
