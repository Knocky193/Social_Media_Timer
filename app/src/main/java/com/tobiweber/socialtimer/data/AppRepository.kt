package com.tobiweber.socialtimer.data

import android.content.Context
import com.tobiweber.socialtimer.alarm.TimerScheduler
import com.tobiweber.socialtimer.notification.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Zentrale Business-Logik für den Timer/Cooldown-Zyklus. Wird sowohl vom
 * AccessibilityService (welche App wird gerade aktiv genutzt) als auch vom TimerAlarmReceiver
 * (Timer/Cooldown abgelaufen) und BootReceiver (Alarme nach Neustart wiederherstellen)
 * verwendet, damit die Regeln an genau einer Stelle stehen.
 *
 * Der Nutzungs-Timer zählt nur echte Nutzung: Eine "Nutzungssitzung" ist offen, solange die
 * App bei eingeschaltetem, entsperrtem Bildschirm im Vordergrund ist. Für die offene Sitzung
 * ist ein Alarm auf den Zeitpunkt gesetzt, an dem das Limit erreicht wäre; beim Pausieren wird
 * er entfernt und die genutzte Zeit aufsummiert.
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
        val updated = existing?.copy(
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
        dao.upsert(updated)
        // Ein geändertes Limit verschiebt das Ende einer gerade laufenden Nutzung.
        catchUp(updated, System.currentTimeMillis())
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean) = stateLock.withLock {
        var app = dao.getByPackageName(packageName) ?: return@withLock
        if (!enabled) app = pauseUsage(app, System.currentTimeMillis())
        dao.update(app.copy(enabled = enabled))
    }

    suspend fun removeApp(app: MonitoredApp) = stateLock.withLock {
        TimerScheduler.cancelAll(context, app.packageName)
        dao.delete(app)
    }

    /**
     * Teilt mit, welche überwachte App gerade aktiv genutzt wird (null = keine, z.B. Bildschirm
     * aus, gesperrt oder eine andere App im Vordergrund). Pausiert alle anderen Sitzungen und
     * startet bzw. setzt die Sitzung der aktiven App fort.
     *
     * Idempotent und schreibt nur bei echten Änderungen – der AccessibilityService ruft dies
     * auch bei jeder Datenbankänderung erneut auf (z.B. damit nach Ablauf des Cooldowns sofort
     * ein neuer Zyklus beginnt, wenn die App noch geöffnet ist).
     */
    suspend fun setActiveUsage(activePackage: String?) = stateLock.withLock {
        val now = System.currentTimeMillis()
        for (app in dao.getAllWithOpenUsage()) {
            if (app.packageName != activePackage) pauseUsage(app, now)
        }
        if (activePackage == null) return@withLock

        var app = dao.getByPackageName(activePackage) ?: return@withLock
        if (!app.enabled) return@withLock
        app = catchUp(app, now)

        when (app.state) {
            AppState.IDLE -> startUsage(
                app.copy(state = AppState.TIMER_RUNNING, usedMillis = 0, cooldownEndAtMillis = null),
                now
            )
            AppState.TIMER_RUNNING -> if (app.usageStartedAtMillis == null) startUsage(app, now)
            AppState.LOCKED_COOLDOWN -> Unit
        }
    }

    /**
     * Beendet nach einem Geräte-Neustart offene Sitzungen, ohne die Zeit seit dem letzten
     * bekannten Start anzurechnen (das Gerät war in der Zwischenzeit aus).
     */
    suspend fun discardOpenUsage() = stateLock.withLock {
        for (app in dao.getAllWithOpenUsage()) {
            TimerScheduler.cancelTimerExpired(context, app.packageName)
            dao.update(app.copy(usageStartedAtMillis = null, lastUsageEndedAtMillis = app.usageStartedAtMillis))
        }
    }

    /**
     * Wird vom TimerAlarmReceiver aufgerufen, wenn das Limit einer laufenden Sitzung erreicht
     * sein müsste. Ist der Timer inzwischen pausiert, passiert nichts.
     */
    suspend fun onTimerExpired(packageName: String) = stateLock.withLock {
        val app = dao.getByPackageName(packageName) ?: return@withLock
        catchUp(app, System.currentTimeMillis())
    }

    /** Wird vom TimerAlarmReceiver aufgerufen, wenn der Cooldown abgelaufen ist. */
    suspend fun onCooldownExpired(packageName: String) = stateLock.withLock {
        val app = dao.getByPackageName(packageName) ?: return@withLock
        catchUp(app, System.currentTimeMillis())
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
            when {
                current.usedMillisAt(now) >= current.limitMillis - EXPIRY_TOLERANCE_MILLIS ->
                    current = expireTimer(current, now)

                current.usageStartedAtMillis != null -> {
                    TimerScheduler.scheduleTimerExpired(context, current.packageName, now + current.remainingMillisAt(now))
                    return current
                }

                // Pausiert: Nach einer Pause so lang wie der Cooldown verfällt die angebrochene Zeit.
                else -> {
                    val pausedSince = current.lastUsageEndedAtMillis ?: now
                    if (now - pausedSince < current.cooldownMillis) return current
                    current = current.copy(state = AppState.IDLE, usedMillis = 0, lastUsageEndedAtMillis = null)
                    dao.update(current)
                    return current
                }
            }
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

    private suspend fun startUsage(app: MonitoredApp, now: Long): MonitoredApp {
        val updated = app.copy(usageStartedAtMillis = now, lastUsageEndedAtMillis = null)
        dao.update(updated)
        TimerScheduler.scheduleTimerExpired(context, app.packageName, now + updated.remainingMillisAt(now))
        return updated
    }

    private suspend fun pauseUsage(app: MonitoredApp, now: Long): MonitoredApp {
        if (app.state != AppState.TIMER_RUNNING || app.usageStartedAtMillis == null) return app

        TimerScheduler.cancelTimerExpired(context, app.packageName)
        val updated = app.copy(
            usedMillis = app.usedMillisAt(now),
            usageStartedAtMillis = null,
            lastUsageEndedAtMillis = now
        )
        if (updated.usedMillis >= updated.limitMillis - EXPIRY_TOLERANCE_MILLIS) {
            return expireTimer(updated, now)
        }
        dao.update(updated)
        return updated
    }

    private suspend fun expireTimer(app: MonitoredApp, now: Long): MonitoredApp {
        if (app.state != AppState.TIMER_RUNNING) return app

        NotificationHelper.showTimeUpNotification(context, app.appName, app.packageName)

        val cooldownEnd = now + app.cooldownMillis
        val updated = app.copy(
            state = AppState.LOCKED_COOLDOWN,
            usedMillis = 0,
            usageStartedAtMillis = null,
            lastUsageEndedAtMillis = null,
            cooldownEndAtMillis = cooldownEnd
        )
        dao.update(updated)
        TimerScheduler.cancelTimerExpired(context, app.packageName)
        TimerScheduler.scheduleCooldownExpired(context, app.packageName, cooldownEnd)
        return updated
    }

    private suspend fun expireCooldown(app: MonitoredApp): MonitoredApp {
        if (app.state != AppState.LOCKED_COOLDOWN) return app

        NotificationHelper.showUnlockedNotification(context, app.appName, app.packageName)

        val updated = app.copy(state = AppState.IDLE, cooldownEndAtMillis = null)
        dao.update(updated)
        TimerScheduler.cancelAll(context, app.packageName)
        return updated
    }

    companion object {
        private val stateLock = Mutex()

        /** Alarme dürfen leicht zu früh feuern; so wird kein Mini-Rest von wenigen ms neu geplant. */
        private const val EXPIRY_TOLERANCE_MILLIS = 1_000L
    }
}
