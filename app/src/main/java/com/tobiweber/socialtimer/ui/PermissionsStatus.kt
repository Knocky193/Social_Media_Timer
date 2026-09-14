package com.tobiweber.socialtimer.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.tobiweber.socialtimer.alarm.TimerScheduler
import com.tobiweber.socialtimer.service.ForegroundAppAccessibilityService

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, ForegroundAppAccessibilityService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledServicesSetting)
    while (splitter.hasNext()) {
        val enabled = ComponentName.unflattenFromString(splitter.next())
        if (enabled == expected) return true
    }
    return false
}

fun areNotificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

fun canScheduleExactAlarms(context: Context): Boolean =
    TimerScheduler.canScheduleExactAlarms(context)
