package com.tobiweber.socialtimer.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.tobiweber.socialtimer.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Beobachtet Fenster-/App-Wechsel des Systems, um zu erkennen, wann eine überwachte
 * App in den Vordergrund kommt. Die Liste der beobachteten Packages wird dynamisch aus
 * der Datenbank aktualisiert und an das System übergeben (serviceInfo.packageNames),
 * damit nur für relevante Apps Events geliefert werden.
 */
class ForegroundAppAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var repository: AppRepository
    private var lastHandledPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = AppRepository(applicationContext)

        repository.observeAll()
            .onEach { apps ->
                val enabledPackages = apps.filter { it.enabled }.map { it.packageName }
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

        // Aufeinanderfolgende Events derselben App (z.B. mehrere Screens innerhalb der App)
        // sollen keinen neuen Timer-Versuch auslösen.
        if (packageName == lastHandledPackage) return
        lastHandledPackage = packageName

        scope.launch {
            repository.onMonitoredAppOpened(packageName)
        }
    }

    override fun onInterrupt() {
        // Nichts zu tun.
    }
}
