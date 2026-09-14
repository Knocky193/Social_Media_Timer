package com.tobiweber.socialtimer.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tobiweber.socialtimer.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(TimerScheduler.EXTRA_PACKAGE_NAME) ?: return
        val action = intent.action ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = AppRepository(context.applicationContext)
                when (action) {
                    TimerScheduler.ACTION_TIMER_EXPIRED -> repository.onTimerExpired(packageName)
                    TimerScheduler.ACTION_COOLDOWN_EXPIRED -> repository.onCooldownExpired(packageName)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
