package com.tobiweber.socialtimer

import android.app.Application
import com.tobiweber.socialtimer.notification.NotificationHelper

class SocialTimerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }
}
