package com.tobiweber.socialtimer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tobiweber.socialtimer.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Stellt Alarme nach Neustart und nach einem App-Update wieder her (in beiden Fällen
 * können vom System gesetzte Alarme verloren gehen).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppRepository(context.applicationContext).resyncAll()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Samsung/HTC "Schnellstart"
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }
}
