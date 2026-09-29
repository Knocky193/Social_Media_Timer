package com.tobiweber.socialtimer.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.tobiweber.socialtimer.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Beobachtet Fenster-/App-Wechsel des Systems, um zu erkennen, wann eine überwachte
 * App in den Vordergrund kommt. Die Liste der beobachteten Packages wird dynamisch aus
 * der Datenbank aktualisiert und an das System übergeben (serviceInfo.packageNames),
 * damit nur für relevante Apps Events geliefert werden.
 *
 * Wichtig: Durch diesen Filter kommen keine Events von Launcher & Co. an, man kann also
 * nicht erkennen, wann eine überwachte App verlassen wurde. Deshalb wird jedes Event einer
 * überwachten App an das Repository weitergegeben – dieses entscheidet anhand des
 * gespeicherten Zustands, ob ein neuer Timer gestartet wird.
 */
class ForegroundAppAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AppRepository

    @Volatile
    private var watchedPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = AppRepository(applicationContext)

        // Der Dienst wird nach einem Kill durch das System neu verbunden – dabei eventuell
        // verlorene Alarme wiederherstellen bzw. überfällige Übergänge nachholen.
        scope.launch { repository.resyncAll() }

        repository.observeAll()
            .onEach { apps ->
                val enabledPackages = apps.filter { it.enabled }.map { it.packageName }
                watchedPackages = enabledPackages.toSet()
                updateWatchedPackages(enabledPackages)
            }
            .launchIn(scope)
    }

    private fun updateWatchedPackages(packages: List<String>) {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.packageNames = if (packages.isEmpty()) null else packages.toTypedArray()
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in watchedPackages) return

        scope.launch {
            repository.onMonitoredAppOpened(packageName)
        }
    }

    override fun onInterrupt() {
        // Nichts zu tun.
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
