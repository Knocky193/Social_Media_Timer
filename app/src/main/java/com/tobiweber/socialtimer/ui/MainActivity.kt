package com.tobiweber.socialtimer.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tobiweber.socialtimer.data.AppRepository
import com.tobiweber.socialtimer.ui.theme.SocialTimerTheme

class MainActivity : ComponentActivity() {

    private val repository by lazy { AppRepository(applicationContext) }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!areNotificationsEnabled(this)) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            SocialTimerTheme {
                Box {
                    SocialTimerRoot(repository)
                }
            }
        }
    }
}

private enum class Screen { LIST, ADD_APP }

@Composable
private fun SocialTimerRoot(repository: AppRepository) {
    var screen by remember { mutableStateOf(Screen.LIST) }

    // Berechtigungen können außerhalb der App (in den Systemeinstellungen) geändert werden,
    // daher bei jedem Wiedereintritt in den Vordergrund neu prüfen.
    var permissionsVersion by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (screen) {
        Screen.LIST -> AppListScreen(
            repository = repository,
            permissionsVersion = permissionsVersion,
            onAddApp = { screen = Screen.ADD_APP }
        )
        Screen.ADD_APP -> AddAppScreen(
            repository = repository,
            onDone = { screen = Screen.LIST }
        )
    }
}
