package com.tobiweber.socialtimer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tobiweber.socialtimer.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppRepository(context.applicationContext).rescheduleAfterBoot()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
