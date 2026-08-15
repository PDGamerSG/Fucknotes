package com.example.fucknotes.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService

/** Wraps [AlarmManager] so the rest of the app only deals in note ids and timestamps. */
object ReminderScheduler {

    fun schedule(context: Context, noteId: Long, title: String, atMillis: Long) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        val pending = pendingIntent(context, noteId, title)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exactAllowed) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            // Without the exact-alarm permission the reminder still fires, just not to the second.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun cancel(context: Context, noteId: Long) {
        val manager = context.getSystemService<AlarmManager>() ?: return
        manager.cancel(pendingIntent(context, noteId, ""))
    }

    private fun pendingIntent(context: Context, noteId: Long, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
