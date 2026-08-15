package com.example.fucknotes.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

object Notifications {

    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_RECORDING = "recording"

    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Note reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Fires when a note reminder is due." }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                "Voice recording",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shown while a voice note is being recorded." }
        )
    }
}
