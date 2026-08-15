package com.example.fucknotes.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.fucknotes.MainActivity
import com.example.fucknotes.R
import com.example.fucknotes.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Posts the notification when a reminder alarm fires, and re-arms all alarms after a reboot. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> notify(
                context,
                intent.getLongExtra(EXTRA_NOTE_ID, -1L),
                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            )

            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> rearm(context)
        }
    }

    private fun notify(context: Context, noteId: Long, title: String) {
        if (noteId <= 0) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val open = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(title.ifBlank { context.getString(R.string.reminder_default_title) })
            .setContentText(context.getString(R.string.reminder_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context).notify(noteId.toInt(), notification)
    }

    private fun rearm(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.notes(context)
                val now = System.currentTimeMillis()
                repo.notesWithReminders().forEach { note ->
                    val at = note.reminderAt ?: return@forEach
                    if (at > now) ReminderScheduler.schedule(context, note.id, note.title, at)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.example.fucknotes.REMINDER"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_TITLE = "title"
    }
}
