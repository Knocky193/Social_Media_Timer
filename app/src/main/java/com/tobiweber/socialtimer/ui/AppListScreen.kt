package com.tobiweber.socialtimer.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import com.tobiweber.socialtimer.data.AppRepository
import com.tobiweber.socialtimer.data.AppState
import com.tobiweber.socialtimer.data.MonitoredApp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    repository: AppRepository,
    permissionsVersion: Int,
    onAddApp: () -> Unit
) {
    val context = LocalContext.current
    val apps by repository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val accessibilityEnabled = remember(permissionsVersion) { isAccessibilityServiceEnabled(context) }
    val notificationsEnabled = remember(permissionsVersion) { areNotificationsEnabled(context) }
    val exactAlarmsAllowed = remember(permissionsVersion) { canScheduleExactAlarms(context) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Social Timer") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddApp) {
                Icon(Icons.Default.Add, contentDescription = "App hinzufügen")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!accessibilityEnabled) {
                item {
                    PermissionBanner(
                        text = "Bedienungshilfen-Dienst ist nicht aktiviert. Ohne ihn wird das Öffnen überwachter Apps nicht erkannt.",
                        buttonText = "Aktivieren",
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )
                }
            }
            if (!notificationsEnabled) {
                item {
                    PermissionBanner(
                        text = "Benachrichtigungen sind deaktiviert. Ohne sie kann keine Routine ausgelöst werden.",
                        buttonText = "Aktivieren",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            context.startActivity(intent)
                        }
                    )
                }
            }
            if (!exactAlarmsAllowed) {
                item {
                    PermissionBanner(
                        text = "Exakte Alarme sind nicht erlaubt. Die Benachrichtigungen könnten dadurch verspätet kommen.",
                        buttonText = "Aktivieren",
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:" + context.packageName))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            if (apps.isEmpty()) {
                item {
                    Text(
                        "Noch keine App hinzugefügt. Tippe auf +, um eine Social-Media-App auszuwählen.",
                        modifier = Modifier.padding(top = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(apps, key = { it.packageName }) { app ->
                MonitoredAppRow(
                    app = app,
                    onSave = { timerMinutes, cooldownMinutes ->
                        scope.launch {
                            repository.addOrUpdateApp(
                                packageName = app.packageName,
                                appName = app.appName,
                                timerMinutes = timerMinutes,
                                cooldownMinutes = cooldownMinutes,
                                enabled = app.enabled
                            )
                        }
                    },
                    onToggleEnabled = { enabled ->
                        scope.launch { repository.setEnabled(app.packageName, enabled) }
                    },
                    onDelete = {
                        scope.launch { repository.removeApp(app) }
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(text: String, buttonText: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onClick) { Text(buttonText) }
        }
    }
}

@Composable
private fun MonitoredAppRow(
    app: MonitoredApp,
    onSave: (timerMinutes: Int, cooldownMinutes: Int) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var timerText by remember(app.packageName) { mutableStateOf(app.timerMinutes.toString()) }
    var cooldownText by remember(app.packageName) { mutableStateOf(app.cooldownMinutes.toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(app.appName, fontWeight = FontWeight.Bold)
                    Text(
                        stateLabel(app),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = app.enabled, onCheckedChange = onToggleEnabled)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Entfernen")
                }
            }

            Spacer(Modifier.width(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = timerText,
                    onValueChange = { value ->
                        timerText = value
                        val minutes = value.toIntOrNull()
                        if (minutes != null && minutes > 0) {
                            onSave(minutes, cooldownText.toIntOrNull() ?: app.cooldownMinutes)
                        }
                    },
                    label = { Text("Timer (Min.)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cooldownText,
                    onValueChange = { value ->
                        cooldownText = value
                        val minutes = value.toIntOrNull()
                        if (minutes != null && minutes > 0) {
                            onSave(timerText.toIntOrNull() ?: app.timerMinutes, minutes)
                        }
                    },
                    label = { Text("Cooldown (Min.)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun stateLabel(app: MonitoredApp): String {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)
    return when (app.state) {
        AppState.IDLE -> if (app.enabled) "Inaktiv – wird beim nächsten Öffnen gestartet" else "Deaktiviert"
        AppState.TIMER_RUNNING -> {
            val end = app.timerEndAtMillis
            if (end != null) "Timer läuft, endet um ${timeFormat.format(Date(end))}" else "Timer läuft"
        }
        AppState.LOCKED_COOLDOWN -> {
            val end = app.cooldownEndAtMillis
            if (end != null) "Cooldown läuft, endet um ${timeFormat.format(Date(end))}" else "Cooldown läuft"
        }
    }
}
