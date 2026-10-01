package com.tobiweber.socialtimer.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Zustand eines überwachten Zyklus:
 * IDLE               -> App ist nicht in einem aktiven Zyklus, nächste Nutzung startet einen neuen Timer.
 * TIMER_RUNNING       -> Nutzungs-Timer ist angebrochen. Er zählt nur, solange die App bei
 *                        eingeschaltetem, entsperrtem Bildschirm im Vordergrund ist
 *                        ([usageStartedAtMillis] != null), sonst ist er pausiert.
 * LOCKED_COOLDOWN     -> Nutzungszeit ist aufgebraucht, "Zeit abgelaufen"-Benachrichtigung wurde
 *                        gesendet, Cooldown läuft (nach Uhrzeit), endet in [cooldownEndAtMillis].
 */
enum class AppState {
    IDLE,
    TIMER_RUNNING,
    LOCKED_COOLDOWN
}

@Entity(tableName = "monitored_apps")
data class MonitoredApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val timerMinutes: Int,
    val cooldownMinutes: Int,
    val enabled: Boolean,
    val state: AppState = AppState.IDLE,
    /** Bereits abgeschlossene Nutzungszeit im aktuellen Zyklus (ohne die laufende Sitzung). */
    @ColumnInfo(defaultValue = "0") val usedMillis: Long = 0,
    /** Beginn der laufenden Nutzungssitzung, null = Timer pausiert. */
    val usageStartedAtMillis: Long? = null,
    /** Ende der letzten Nutzungssitzung; nach einer Pause von [cooldownMinutes] verfällt die Restzeit. */
    val lastUsageEndedAtMillis: Long? = null,
    val cooldownEndAtMillis: Long? = null
) {
    val limitMillis: Long get() = timerMinutes * 60_000L
    val cooldownMillis: Long get() = cooldownMinutes * 60_000L

    /** Genutzte Zeit inklusive der gerade laufenden Sitzung. */
    fun usedMillisAt(now: Long): Long =
        usedMillis + (usageStartedAtMillis?.let { (now - it).coerceAtLeast(0) } ?: 0)

    fun remainingMillisAt(now: Long): Long = (limitMillis - usedMillisAt(now)).coerceAtLeast(0)
}
