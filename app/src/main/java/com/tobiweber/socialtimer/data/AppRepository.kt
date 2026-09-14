package com.tobiweber.socialtimer.data

import android.content.Context
import com.tobiweber.socialtimer.alarm.TimerScheduler
import com.tobiweber.socialtimer.notification.NotificationHelper
import kotlinx.coroutines.flow.Flow

/**
 * Zentrale Business-Logik für den Timer/Cooldown-Zyklus. Wird sowohl vom
 * AccessibilityService (App geöffnet) als auch vom TimerAlarmReceiver
 * (Timer/Cooldown abgelaufen) und BootReceiver (Alarme nach Neustart wiederherstellen)
 * verwendet, damit die Regeln an genau einer Stelle stehen.
 */
class AppRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).monitoredAppDao()

    fun observeAll(): Flow<List<MonitoredApp>> = dao.observeAll()

    suspend fun getByPackageName(packageName: String): MonitoredApp? =
        dao.getByPackageName(packageName)

    suspend fun addOrUpdateApp(
        packageName: String,
        appName: String,
        timerMinutes: Int,
        cooldownMinutes: Int,
        enabled: Boolean
    ) {
        val existing = dao.getByPackageName(packageName)
        dao.upsert(
            existing?.copy(
                appName = appName,
                timerMinutes = timerMinutes,
                cooldownMinutes = cooldownMinutes,
                enabled = enabled
            ) ?: MonitoredApp(
                packageName = packageName,
                appName = appName,
                timerMinutes = timerMinutes,
                cooldownMinutes = cooldownMinutes,
                enabled = enabled
            )
        )
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean) {
        val app = dao.getByPackageName(packageName) ?: return
        dao.update(app.copy(enabled = enabled))
    }

    suspend fun removeApp(app: MonitoredApp) {
        TimerScheduler.cancelAll(context, app.packageName)
        dao.delete(app)
    }

    /**
     * Wird aufgerufen, wenn der AccessibilityService erkennt, dass eine überwachte
     * App in den Vordergrund kommt. Startet nur dann einen neuen Timer, wenn gerade
     * kein Zyklus für diese App läuft (Wall-Clock-Timer: läuft weiter, auch wenn die
     * App zwischenzeitlich verlassen wird).
     */
    suspend fun onMonitoredAppOpened(packageName: String) {
        val app = dao.getByPackageName(packageName) ?: return
        if (!app.enabled) return
        if (app.state != AppState.IDLE) return

        val timerEnd = System.currentTimeMillis() + app.timerMinutes * 60_000L
        dao.update(app.copy(state = AppState.TIMER_RUNNING, timerEndAtMillis = timerEnd, cooldownEndAtMillis = null))
        TimerScheduler.scheduleTimerExpired(context, packageName, timerEnd)
    }

    /** Wird vom TimerAlarmReceiver aufgerufen, wenn der Nutzungs-Timer abgelaufen ist. */
    suspend fun onTimerExpired(packageName: String) {
        val app = dao.getByPackageName(packageName) ?: return
        if (app.state != AppState.TIMER_RUNNING) return

        NotificationHelper.showTimeUpNotification(context, app.appName, packageName)

        val cooldownEnd = System.currentTimeMillis() + app.cooldownMinutes * 60_000L
        dao.update(app.copy(state = AppState.LOCKED_COOLDOWN, timerEndAtMillis = null, cooldownEndAtMillis = cooldownEnd))
        TimerScheduler.scheduleCooldownExpired(context, packageName, cooldownEnd)
    }

    /** Wird vom TimerAlarmReceiver aufgerufen, wenn der Cooldown abgelaufen ist. */
    suspend fun onCooldownExpired(packageName: String) {
        val app = dao.getByPackageName(packageName) ?: return
        if (app.state != AppState.LOCKED_COOLDOWN) return

        NotificationHelper.showUnlockedNotification(context, app.appName, packageName)

        dao.update(app.copy(state = AppState.IDLE, timerEndAtMillis = null, cooldownEndAtMillis = null))
    }

    /** Stellt nach einem Geräte-Neustart alle noch offenen Alarme wieder her. */
    suspend fun rescheduleAfterBoot() {
        val now = System.currentTimeMillis()
        for (app in dao.getAllWithActiveCycle()) {
            when (app.state) {
                AppState.TIMER_RUNNING -> {
                    val end = app.timerEndAtMillis
                    if (end != null && end > now) {
                        TimerScheduler.scheduleTimerExpired(context, app.packageName, end)
                    } else {
                        // Alarm ist während des Neustarts bereits abgelaufen -> sofort nachholen.
                        onTimerExpired(app.packageName)
                    }
                }
                AppState.LOCKED_COOLDOWN -> {
                    val end = app.cooldownEndAtMillis
                    if (end != null && end > now) {
                        TimerScheduler.scheduleCooldownExpired(context, app.packageName, end)
                    } else {
                        onCooldownExpired(app.packageName)
                    }
                }
                AppState.IDLE -> Unit
            }
        }
    }
}
