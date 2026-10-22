package com.example.fucknotes

import android.app.Application
import android.content.Context
import com.example.fucknotes.data.backup.BackupManager
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.drive.DriveAuth
import com.example.fucknotes.drive.DriveSync
import com.example.fucknotes.ui.editor.tabs.OpenTabs
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
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
    @Volatile private var drive: DriveSync? = null
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

    fun drive(context: Context): DriveSync = drive ?: synchronized(this) {
        drive ?: DriveSync(
            context.applicationContext, AppDatabase.get(context), notes(context), settings(context),
            DriveAuth(context.applicationContext),
        ).also { drive = it }
    }

    /** Short writes that must survive a screen being popped, such as adding a notebook sheet. */
    fun persistenceScope(): CoroutineScope = persistence
}

class NotesApp : Application(), ImageLoaderFactory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * One loader for every cover and attachment thumbnail: a quarter of the heap as memory cache
     * so a scrolled shelf never re-decodes, no crossfade so a tile lands painted, and no disk
     * cache because every image is already a local file.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache(null)
        .crossfade(false)
        .build()

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // Trash keeps notes for 30 days, then drops them for good.
            ServiceLocator.notes(this@NotesApp).purgeOldTrash()
        }
        // The tab bar outlives any screen: restore it, then keep it saved.
        OpenTabs.bind(ServiceLocator.notes(this), ServiceLocator.settings(this), scope)
    }
}
