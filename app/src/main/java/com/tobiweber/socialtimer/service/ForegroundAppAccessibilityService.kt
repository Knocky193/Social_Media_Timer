package com.tobiweber.socialtimer.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.tobiweber.socialtimer.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Ermittelt, welche überwachte App gerade *aktiv genutzt* wird, und meldet das an das
 * Repository, das danach den Nutzungs-Timer startet oder pausiert.
 *
 * Aktiv genutzt heißt:
 *  - der Bildschirm ist an und das Gerät entsperrt (Bildschirm aus / Sperrbildschirm = Pause),
 *  - ein Fenster der App ist sichtbar im Vordergrund (minimiert, im Hintergrund oder vom
 *    Launcher bzw. einer anderen App verdeckt = Pause).
 *
 * Für die Vordergrund-Erkennung werden die sichtbaren App-Fenster abgefragt statt nur das
 * Paket des letzten Events zu verwenden. So pausiert z.B. die eingeblendete Tastatur oder ein
 * Systemdialog den Timer nicht, und es werden Events aller Apps benötigt, um zu erkennen, wann
 * eine überwachte App verlassen wird. Von den Fenstern wird nur der Paketname gelesen.
 */
class ForegroundAppAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: AppRepository

    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }

    /** Paketnamen aller aktivierten, überwachten Apps. */
    private var monitoredPackages: Set<String> = emptySet()

    /** Paket des letzten Fensterwechsel-Events, Fallback falls keine Fensterliste verfügbar ist. */
    private var lastEventPackage: String? = null

    /** Die überwachte App, die gerade aktiv genutzt wird, oder null. */
    private val activePackage = MutableStateFlow<String?>(null)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            evaluate()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = AppRepository(applicationContext)

        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            RECEIVER_NOT_EXPORTED
        )

        repository.observeAll()
            .onEach { apps ->
                monitoredPackages = apps.filter { it.enabled }.map { it.packageName }.toSet()
                evaluate()
            }
            .launchIn(scope)

        // Die aktive App bei jeder Änderung – und zusätzlich bei jeder Datenbankänderung – an das
        // Repository melden. Letzteres startet z.B. direkt einen neuen Zyklus, wenn der Cooldown
        // abläuft, während die App noch offen ist. setActiveUsage ist idempotent.
        // Beim (Neu-)Verbinden werden dabei auch Sitzungen geschlossen, die offen geblieben sind,
        // weil der Dienst zwischenzeitlich beendet wurde.
        combine(activePackage, repository.observeAll()) { pkg, _ -> pkg }
            .onEach { repository.setActiveUsage(it) }
            .launchIn(scope)

        // Verlorene Alarme wiederherstellen bzw. überfällige Übergänge nachholen.
        scope.launch { repository.resyncAll() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                event.packageName?.toString()?.let { lastEventPackage = it }
                evaluate()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> evaluate()
        }
    }

    /** Bestimmt die aktiv genutzte überwachte App neu. Läuft auf dem Main-Thread. */
    private fun evaluate() {
        activePackage.value = if (isUserActive()) foregroundMonitoredPackage() else null
    }

    private fun isUserActive(): Boolean =
        powerManager.isInteractive && !keyguardManager.isKeyguardLocked

    private fun foregroundMonitoredPackage(): String? {
        if (monitoredPackages.isEmpty()) return null

        val appWindows = try {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        } catch (e: RuntimeException) {
            emptyList()
        }
        if (appWindows.isEmpty()) {
            return lastEventPackage?.takeIf { it in monitoredPackages }
        }

        // Die Fensterliste enthält nur sichtbare Fenster. Bei Split-Screen o.Ä. das aktive
        // Fenster bevorzugen, sonst zählt jedes sichtbare Fenster einer überwachten App.
        val sorted = appWindows.sortedByDescending { it.isActive }
        return sorted
            .mapNotNull { it.root?.packageName?.toString() }
            .firstOrNull { it in monitoredPackages }
    }

    override fun onInterrupt() {
        // Nichts zu tun.
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // War nicht registriert (onServiceConnected wurde nie aufgerufen).
        }
        scope.cancel()
        // Dienst wird deaktiviert: Ohne Erkennung darf keine Sitzung offen weiterzählen.
        if (::repository.isInitialized) {
            CoroutineScope(Dispatchers.IO).launch { repository.setActiveUsage(null) }
        }
        super.onDestroy()
    }
}
