package com.tobiweber.socialtimer.util

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

object InstalledAppsProvider {

    /** Liefert alle Apps mit einem Launcher-Icon (installierte, startbare Apps), außer der eigenen App. */
    fun getLaunchableApps(context: android.content.Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ownPackage = context.packageName

        return pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ownPackage }
            .map { appInfo: ApplicationInfo ->
                InstalledAppInfo(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }
}
