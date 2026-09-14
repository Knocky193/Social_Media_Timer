package com.tobiweber.socialtimer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.tobiweber.socialtimer.data.AppRepository
import com.tobiweber.socialtimer.util.InstalledAppInfo
import com.tobiweber.socialtimer.util.InstalledAppsProvider
import kotlinx.coroutines.launch

private const val DEFAULT_TIMER_MINUTES = 30
private const val DEFAULT_COOLDOWN_MINUTES = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppScreen(repository: AppRepository, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allApps = InstalledAppsProvider.getLaunchableApps(context)
    }

    val filtered = if (query.isBlank()) {
        allApps
    } else {
        allApps.filter { it.appName.contains(query, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App auswählen") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Suchen") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
            items(filtered, key = { it.packageName }) { info ->
                ListItem(
                    headlineContent = { Text(info.appName) },
                    supportingContent = { Text(info.packageName) },
                    leadingContent = {
                        val bitmap = remember(info.packageName) { info.icon?.toBitmap()?.asImageBitmap() }
                        if (bitmap != null) {
                            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clickable {
                            scope.launch {
                                repository.addOrUpdateApp(
                                    packageName = info.packageName,
                                    appName = info.appName,
                                    timerMinutes = DEFAULT_TIMER_MINUTES,
                                    cooldownMinutes = DEFAULT_COOLDOWN_MINUTES,
                                    enabled = true
                                )
                                onDone()
                            }
                        }
                )
            }
        }
    }
}
