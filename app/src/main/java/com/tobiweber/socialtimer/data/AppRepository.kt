package com.tobiweber.socialtimer.data

import android.content.Context
import com.tobiweber.socialtimer.alarm.TimerScheduler
import com.tobiweber.socialtimer.notification.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Zentrale Business-Logik für den Timer/Cooldown-Zyklus. Wird sowohl vom
 * AccessibilityService (App geöffnet) als auch vom TimerAlarmReceiver
 * (Timer/Cooldown abgelaufen) und BootReceiver (Alarme nach Neustart wiederherstellen)
 * verwendet, damit die Regeln an genau einer Stelle stehen.
 *
 * Alle Zustandsübergänge laufen über einen prozessweiten Mutex, damit sich Alarm-Receiver
 * und AccessibilityService nicht gegenseitig überschreiben.
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
    ) = stateLock.withLock {
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

    suspend fun setEnabled(packageName: String, enabled: Boolean) = stateLock.withLock {
        val app = dao.getByPackageName(packageName) ?: return@withLock
        dao.update(app.copy(enabled = enabled))
    }

    suspend fun removeApp(app: MonitoredApp) = stateLock.withLock {
        TimerScheduler.cancelAll(context, app.packageName)
        dao.delete(app)
    }

    /**
     * Wird aufgerufen, wenn der AccessibilityService erkennt, dass eine überwachte
     * App im Vordergrund ist. Startet nur dann einen neuen Timer, wenn gerade
     * kein Zyklus für diese App läuft (Wall-Clock-Timer: läuft weiter, auch wenn die
     * App zwischenzeitlich verlassen wird).
     *
     * Zusätzlich wird hier ein eventuell verpasster Alarm nachgeholt (z.B. weil das System
     * den Alarm durch Energiesparmaßnahmen verworfen hat), damit ein Zyklus nie dauerhaft
     * in TIMER_RUNNING oder LOCKED_COOLDOWN hängen bleibt.
     */
    suspend fun onMonitoredAppOpened(packageName: String) = stateLock.withLock {
        var app = dao.getByPackageName(packageName) ?: return@withLock
        if (!app.enabled) return@withLock

        app = catchUp(app, System.currentTimeMillis())

        if (app.state == AppState.IDLE) {
            val timerEnd = System.currentTimeMillis() + app.timerMinutes * 60_000L
            dao.update(app.copy(state = AppState.TIMER_RUNNING, timerEndAtMillis = timerEnd, cooldownEndAtMillis = null))
            TimerScheduler.scheduleTimerExpired(context, packageName, timerEnd)
        }
    }

    /** Wird vom TimerAlarmReceiver aufgerufen, wenn der Nutzungs-Timer abgelaufen ist. */
    suspend fun onTimerExpired(packageName: String) = stateLock.withLock {
        val app = dao.getByPackageName(packageName) ?: return@withLock
        expireTimer(app)
    }

    /** Wird vom TimerAlarmReceiver aufgerufen, wenn der Cooldown abgelaufen ist. */
    suspend fun onCooldownExpired(packageName: String) = stateLock.withLock {
        val app = dao.getByPackageName(packageName) ?: return@withLock
        expireCooldown(app)
    }

    /**
     * Gleicht alle laufenden Zyklen mit der aktuellen Uhrzeit ab: überfällige Übergänge
     * werden sofort nachgeholt, alle anderen Alarme neu gesetzt. Idempotent – wird nach
     * Neustart, App-Update, Start des AccessibilityService und beim Öffnen der App aufgerufen,
     * da Alarme durch Force-Stop, Updates oder aggressive Energiesparmodi verloren gehen können.
     */
    suspend fun resyncAll() = stateLock.withLock {
        val now = System.currentTimeMillis()
        for (app in dao.getAllWithActiveCycle()) {
            catchUp(app, now)
        }
    }

    /** Holt überfällige Übergänge nach bzw. setzt den Alarm für den aktuellen Zustand neu. */
    private suspend fun catchUp(app: MonitoredApp, now: Long): MonitoredApp {
        var current = app
        if (current.state == AppState.TIMER_RUNNING) {
            val end = current.timerEndAtMillis
            if (end != null && end > now) {
                TimerScheduler.scheduleTimerExpired(context, current.packageName, end)
                return current
            }
            current = expireTimer(current)
        }
        if (current.state == AppState.LOCKED_COOLDOWN) {
            val end = current.cooldownEndAtMillis
            if (end != null && end > now) {
                TimerScheduler.scheduleCooldownExpired(context, current.packageName, end)
                return current
            }
            current = expireCooldown(current)
        }
        return current
    }

    private suspend fun expireTimer(app: MonitoredApp): MonitoredApp {
        if (app.state != AppState.TIMER_RUNNING) return app

        NotificationHelper.showTimeUpNotification(context, app.appName, app.packageName)

        val cooldownEnd = System.currentTimeMillis() + app.cooldownMinutes * 60_000L
        val updated = app.copy(state = AppState.LOCKED_COOLDOWN, timerEndAtMillis = null, cooldownEndAtMillis = cooldownEnd)
        dao.update(updated)
        TimerScheduler.scheduleCooldownExpired(context, app.packageName, cooldownEnd)
        return updated
    }

    private suspend fun expireCooldown(app: MonitoredApp): MonitoredApp {
        if (app.state != AppState.LOCKED_COOLDOWN) return app

        NotificationHelper.showUnlockedNotification(context, app.appName, app.packageName)

        val updated = app.copy(state = AppState.IDLE, timerEndAtMillis = null, cooldownEndAtMillis = null)
        dao.update(updated)
        TimerScheduler.cancelAll(context, app.packageName)
        return updated
    }

    companion object {
        private val stateLock = Mutex()
    }
}
