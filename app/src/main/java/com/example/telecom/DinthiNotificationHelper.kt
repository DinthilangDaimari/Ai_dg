package com.example.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object DinthiNotificationHelper {

    const val CHANNEL_ID_CALLS = "dinthi_urgent_calls"
    const val CHANNEL_NAME_CALLS = "Urgent Call Summaries & Reminders"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_CALLS,
                CHANNEL_NAME_CALLS,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when Dinthi AI finishes screening an urgent call or when a scheduled call reminder is due."
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showUrgentCallNotification(
        context: Context,
        callId: String,
        callerName: String,
        reason: String,
        phoneNumber: String
    ) {
        createNotificationChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_call_id", callId)
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Callback dial intent
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        val pendingDial = PendingIntent.getActivity(
            context,
            phoneNumber.hashCode(),
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Urgent Call Screened: $callerName")
            .setContentText(reason.ifBlank { "Dinthi AI completed screening this call." })
            .setStyle(NotificationCompat.BigTextStyle().bigText("Caller: $callerName ($phoneNumber)\nReason: $reason"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_call, "Call Back", pendingDial)
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(callId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Handled when notification permission is pending
        }
    }

    fun showScheduledCallReminder(
        context: Context,
        recipientName: String,
        agenda: String,
        phoneNumber: String
    ) {
        createNotificationChannels(context)

        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        val pendingDial = PendingIntent.getActivity(
            context,
            (phoneNumber + "sched").hashCode(),
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CALLS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Scheduled Call Reminder: $recipientName")
            .setContentText(agenda.ifBlank { "You scheduled a call with $recipientName." })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_call, "Place Call", pendingDial)
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify((recipientName + phoneNumber).hashCode(), notification)
        } catch (_: SecurityException) {}
    }
}
