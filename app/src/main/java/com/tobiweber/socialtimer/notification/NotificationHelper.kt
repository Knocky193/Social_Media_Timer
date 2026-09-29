package com.tobiweber.socialtimer.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tobiweber.socialtimer.R

/**
 * Erstellt zwei klar unterscheidbare Benachrichtigungs-Kanäle/Texte, damit sie in
 * "Modi und Routinen" zuverlässig als Bedingung ("Benachrichtigung erhalten", Text
 * enthält "...") ausgewählt werden können.
 */
object NotificationHelper {

    const val CHANNEL_TIME_UP = "social_timer_time_up"
    const val CHANNEL_UNLOCKED = "social_timer_unlocked"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val timeUpChannel = NotificationChannel(
            CHANNEL_TIME_UP,
            "Social-Media-Zeit abgelaufen",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Wird gesendet, sobald der Nutzungs-Timer einer überwachten App abgelaufen ist."
        }

        val unlockedChannel = NotificationChannel(
            CHANNEL_UNLOCKED,
            "App wieder freigegeben",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Wird gesendet, sobald die Cooldown-Pause einer überwachten App abgelaufen ist."
        }

        manager.createNotificationChannel(timeUpChannel)
        manager.createNotificationChannel(unlockedChannel)
    }

    fun showTimeUpNotification(context: Context, appName: String, packageName: String) {
        cancel(context, notificationIdFor(packageName, CHANNEL_UNLOCKED))
        show(
            context = context,
            channelId = CHANNEL_TIME_UP,
            notificationId = notificationIdFor(packageName, CHANNEL_TIME_UP),
            title = "$appName-Zeit abgelaufen",
            text = "Deine Social-Media-Zeit für $appName ist vorbei."
        )
    }

    fun showUnlockedNotification(context: Context, appName: String, packageName: String) {
        cancel(context, notificationIdFor(packageName, CHANNEL_TIME_UP))
        show(
            context = context,
            channelId = CHANNEL_UNLOCKED,
            notificationId = notificationIdFor(packageName, CHANNEL_UNLOCKED),
            title = "$appName wieder freigegeben",
            text = "Die Cooldown-Pause für $appName ist vorbei, du kannst die App wieder nutzen."
        )
    }

    private fun show(context: Context, channelId: String, notificationId: Int, title: String, text: String) {
        ensureChannels(context)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build()

        // Eine noch sichtbare Benachrichtigung aus dem letzten Zyklus zuerst entfernen:
        // Ein reines Update derselben ID wird von "Modi und Routinen" nicht zuverlässig als
        // neue Benachrichtigung erkannt, die Routine würde dann ab dem 2. Zyklus nicht mehr greifen.
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(notificationId)
        manager.notify(notificationId, notification)
    }

    private fun cancel(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun notificationIdFor(packageName: String, channelId: String): Int =
        (packageName + channelId).hashCode()
}
