package com.tobiweber.socialtimer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Zustand eines überwachten Zyklus:
 * IDLE               -> App ist nicht in einem aktiven Zyklus, nächster App-Start startet einen neuen Timer.
 * TIMER_RUNNING       -> Nutzungs-Timer läuft, endet in [timerEndAtMillis].
 * LOCKED_COOLDOWN     -> Timer ist abgelaufen, "Zeit abgelaufen"-Benachrichtigung wurde gesendet,
 *                        Cooldown läuft, endet in [cooldownEndAtMillis].
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
    val timerEndAtMillis: Long? = null,
    val cooldownEndAtMillis: Long? = null
)
