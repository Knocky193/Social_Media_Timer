package com.tobiweber.socialtimer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Kapselt das Setzen exakter Alarme für Timer-Ende und Cooldown-Ende. Es wird
 * setExactAndAllowWhileIdle verwendet, damit die Benachrichtigung auch im Doze-Modus
 * pünktlich ausgelöst wird (wichtig, da die Routine in "Modi und Routinen" darauf reagiert).
 */
object TimerScheduler {

    const val ACTION_TIMER_EXPIRED = "com.tobiweber.socialtimer.ACTION_TIMER_EXPIRED"
    const val ACTION_COOLDOWN_EXPIRED = "com.tobiweber.socialtimer.ACTION_COOLDOWN_EXPIRED"
    const val EXTRA_PACKAGE_NAME = "extra_package_name"

    fun scheduleTimerExpired(context: Context, packageName: String, triggerAtMillis: Long) {
        schedule(context, ACTION_TIMER_EXPIRED, packageName, triggerAtMillis)
    }

    fun scheduleCooldownExpired(context: Context, packageName: String, triggerAtMillis: Long) {
        schedule(context, ACTION_COOLDOWN_EXPIRED, packageName, triggerAtMillis)
    }

    fun cancelAll(context: Context, packageName: String) {
        cancel(context, ACTION_TIMER_EXPIRED, packageName)
        cancel(context, ACTION_COOLDOWN_EXPIRED, packageName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return alarmManager.canScheduleExactAlarms()
    }

    private fun schedule(context: Context, action: String, packageName: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntentFor(context, action, packageName)

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            // Fallback, falls der Nutzer die Berechtigung für exakte Alarme nicht erteilt hat.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancel(context: Context, action: String, packageName: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntentFor(context, action, packageName))
    }

    private fun pendingIntentFor(context: Context, action: String, packageName: String): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_PACKAGE_NAME, packageName)
        }
        val requestCode = (action + packageName).hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
