package com.veygax.eventhorizon

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class AppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val useDarkTheme = isSystemInDarkTheme()
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val ctx = LocalContext.current
                if (useDarkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                if (useDarkTheme) darkColorScheme() else lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppsScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen() {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    val sharedPrefs = remember { context.getSharedPreferences("eventhorizon_prefs", Context.MODE_PRIVATE) }

    var installStatus by remember { mutableStateOf("Ready to install.") }
    var isInstalling by remember { mutableStateOf(false) }

    // State for Dogfood Hub feature
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartDialogContent by remember { mutableStateOf<Pair<String, () -> Unit>>(Pair("", {})) }
    var isDogfoodEnabled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Check Dogfood Hub status
        val buildType = RootUtils.runAsRoot("getprop ro.build.type")
        isDogfoodEnabled = buildType.trim() == "userdebug"

        // Check for Dogfood Hub setup step 2
        if (sharedPrefs.getBoolean("dogfood_pending_step2", false)) {
            sharedPrefs.edit().remove("dogfood_pending_step2").apply()
            RootUtils.runAsRoot(DogfoodCommands.ENABLE_DOGFOOD_STEP_2)
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restart Required") },
            text = { Text(restartDialogContent.first) },
            confirmButton = { Button(onClick = { restartDialogContent.second(); showRestartDialog = false }) { Text("Confirm") } },
            dismissButton = { Button(onClick = { showRestartDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps") },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Dogfood Hub card is now first
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(text = "Dogfood Hub", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Enables Dogfood Hub", style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Switch(checked = isDogfoodEnabled, onCheckedChange = { isEnabled ->
                                restartDialogContent = if (isEnabled) {
                                    Pair("This will restart your device's interface. After it reloads, please open this app again to complete the second step automatically.") {
                                        coroutineScope.launch {
                                            sharedPrefs.edit().putBoolean("dogfood_pending_step2", true).apply()
                                            RootUtils.runAsRoot(DogfoodCommands.ENABLE_DOGFOOD_STEP_1)
                                        }
                                    }
                                } else {
                                    Pair("This will disable the Dogfood Hub and restart your device's interface.") {
                                        coroutineScope.launch { RootUtils.runAsRoot(DogfoodCommands.DISABLE_DOGFOOD_HUB) }
                                    }
                                }
                                showRestartDialog = true
                            })
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { coroutineScope.launch { RootUtils.runAsRoot(DogfoodCommands.LAUNCH_DOGFOOD_HUB) } },
                                enabled = isDogfoodEnabled
                            ) { Text("Launch") }
                        }
                    }
                }
            }
            item {
                AppCard(
                    title = "Dock Editor",
                    description = "A simple editor for the Quest's dock bar.",
                    status = installStatus
                ) {
                    Button(
                        onClick = {
                            isInstalling = true
                            coroutineScope.launch {
                                AppInstaller.downloadAndInstall(
                                    context = context,
                                    owner = "Lumince",
                                    repo = "DockEditor",
                                    onStatusUpdate = { newStatus ->
                                        installStatus = newStatus
                                    }
                                )
                                isInstalling = false
                            }
                        },
                        enabled = !isInstalling
                    ) {
                        Text(if (isInstalling) "Installing..." else "Install")
                    }
                }
            }
        }
    }
}

@Composable
fun AppCard(title: String, description: String, status: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                }
                content()
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Status: $status",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Moved Dogfood commands here to keep them self-contained with the activity
object DogfoodCommands {
    const val ENABLE_DOGFOOD_STEP_1 = "magisk resetprop ro.build.type userdebug\nstop\nstart"
    const val ENABLE_DOGFOOD_STEP_2 = "am broadcast -a oculus.intent.action.DC_OVERRIDE --esa config_param_value oculus_systemshell:oculus_is_trusted_user:true\nstop\nstart"
    const val DISABLE_DOGFOOD_HUB = "magisk resetprop --delete ro.build.type\nstop\nstart"
    const val LAUNCH_DOGFOOD_HUB = "am start com.oculus.vrshell/com.oculus.panelapp.dogfood.DogfoodMainActivity"
}

@Preview(showBackground = true, heightDp = 600)
@Composable
fun AppsScreenPreview() {
    MaterialTheme {
        AppsScreen()
    }
}