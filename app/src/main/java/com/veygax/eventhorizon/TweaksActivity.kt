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
import java.io.File

class TweaksActivity : ComponentActivity() {
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
                    TweaksScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TweaksScreen() {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = LocalContext.current as? Activity
    val sharedPrefs = remember { context.getSharedPreferences("eventhorizon_prefs", Context.MODE_PRIVATE) }
    val scriptFile = remember { File(context.filesDir, "rgb_led.sh") }

    // --- State for the UI ---
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartDialogContent by remember { mutableStateOf<Pair<String, () -> Unit>>(Pair("", {})) }

    var runOnBoot by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("rgb_on_boot", false)) }
    var isRgbExecuting by remember { mutableStateOf(false) }
    var isDogfoodEnabled by rememberSaveable { mutableStateOf(false) }

    // State variables to be populated by device checks
    var uiSwitchState by rememberSaveable { mutableStateOf(0) } // 0=Dock, 1=Navigator
    var isVoidTransitionEnabled by rememberSaveable { mutableStateOf(false) }
    var isTeleportLimitDisabled by rememberSaveable { mutableStateOf(false) }
    var isNavigatorFogEnabled by rememberSaveable { mutableStateOf(false) }
    var isPanelScalingEnabled by rememberSaveable { mutableStateOf(false) }
    var isInfinitePanelsEnabled by rememberSaveable { mutableStateOf(false) }


    // This effect runs once to get the initial state of all tweaks
    LaunchedEffect(Unit) {
        val pid = RootUtils.runAsRoot("pgrep -f ${scriptFile.name}")
        isRgbExecuting = pid.trim().toIntOrNull() != null

        // Check Dogfood Hub status
        val buildType = RootUtils.runAsRoot("getprop ro.build.type")
        isDogfoodEnabled = buildType.trim() == "userdebug"

        // Check UI Mode (Navigator vs Dock)
        val uiStateValue = RootUtils.runAsRoot("oculuspreferences --getc debug_navigator_state")
        uiSwitchState = if (uiStateValue.contains(": 1")) 1 else 0

        // Check Transition Mode (Void vs Immersive)
        val transitionValue = RootUtils.runAsRoot("oculuspreferences --getc shell_immersive_transitions_enabled")
        isVoidTransitionEnabled = transitionValue.contains(": false") // Void is ON when immersive is OFF

        // Check Teleport Limit
        val teleportValue = RootUtils.runAsRoot("oculuspreferences --getc shell_teleport_anywhere")
        isTeleportLimitDisabled = teleportValue.contains(": true")

        // Check Navigator Fog
        val fogValue = RootUtils.runAsRoot("oculuspreferences --getc navigator_background_disabled")
        isNavigatorFogEnabled = fogValue.contains(": false") // Fog is ON when disabled is OFF

        // Check Panel Scaling
        val panelScalingValue = RootUtils.runAsRoot("oculuspreferences --getc panel_scaling")
        isPanelScalingEnabled = panelScalingValue.contains(": true")

        // Check Infinite Panels
        val infinitePanelsValue = RootUtils.runAsRoot("oculuspreferences --getc debug_infinite_spatial_panels_enabled")
        isInfinitePanelsEnabled = infinitePanelsValue.contains(": true")

        // Check for Dogfood Hub setup step 2
        if (sharedPrefs.getBoolean("dogfood_pending_step2", false)) {
            sharedPrefs.edit().remove("dogfood_pending_step2").apply()
            RootUtils.runAsRoot(TweakCommands.ENABLE_DOGFOOD_STEP_2)
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("eventhorizon AIO") },
                navigationIcon = { IconButton(onClick = { activity?.finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                TweakCard("Rainbow LED", "Cyclesnotification LED through colors.") {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Run on Boot", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = runOnBoot, onCheckedChange = { checked ->
                                runOnBoot = checked
                                sharedPrefs.edit().putBoolean("rgb_on_boot", checked).apply()
                                coroutineScope.launch { snackbarHostState.showSnackbar(if (checked) "RGB on Boot Enabled" else "RGB on Boot Disabled") }
                            })
                        }
                        Spacer(Modifier.height(8.dp))
                        // Reverted to simple, optimistic logic
                        Button(onClick = {
                            coroutineScope.launch {
                                if (isRgbExecuting) {
                                    isRgbExecuting = false
                                    RootUtils.runAsRoot("pkill -f ${scriptFile.name}")
                                    RootUtils.runAsRoot(TweakCommands.LEDS_OFF)
                                    snackbarHostState.showSnackbar("RGB script stopped.")
                                } else {
                                    isRgbExecuting = true
                                    scriptFile.writeText(TweakCommands.RGB_SCRIPT)
                                    RootUtils.runAsRoot("chmod +x ${scriptFile.absolutePath}")
                                    RootUtils.runAsRoot("${scriptFile.absolutePath} &")
                                    snackbarHostState.showSnackbar("RGB script started.")
                                }
                            }
                        }) { Text(if (isRgbExecuting) "Stop" else "Start") }
                    }
                }
            }
            item {
                TweakCard("UI Switching", "Switches between Navigator and Dock without rebooting") {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (uiSwitchState == 1) "Navigator UI" else "Dock UI", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = uiSwitchState == 1, onCheckedChange = { isNavigator ->
                            uiSwitchState = if (isNavigator) 1 else 0
                            coroutineScope.launch {
                                val command = if (isNavigator) TweakCommands.SET_UI_NAVIGATOR else TweakCommands.SET_UI_DOCK
                                RootUtils.runAsRoot(command)
                                snackbarHostState.showSnackbar("Switched to ${if (isNavigator) "Navigator" else "Dock"} UI.")
                            }
                        })
                    }
                }
            }
            item {
                TweakCard("Void Transition", "Switches between Immersive transition and Void Transition") {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (isVoidTransitionEnabled) "Void Transition" else "Immersive Transition", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isVoidTransitionEnabled, onCheckedChange = { isEnabled ->
                            isVoidTransitionEnabled = isEnabled
                            coroutineScope.launch {
                                val command = if (isEnabled) TweakCommands.SET_TRANSITION_VOID else TweakCommands.SET_TRANSITION_IMMERSIVE
                                RootUtils.runAsRoot(command)
                                snackbarHostState.showSnackbar(if (isEnabled) "Void Transition Enabled." else "Immersive Transition Enabled.")
                            }
                        })
                    }
                }
            }
            item {
                TweakCard("Teleport Anywhere", "Teleport anywhere in the home environment") {
                    Switch(checked = isTeleportLimitDisabled, onCheckedChange = { isEnabled ->
                        isTeleportLimitDisabled = isEnabled
                        coroutineScope.launch {
                            val command = if (isEnabled) TweakCommands.DISABLE_TELEPORT_LIMIT else TweakCommands.ENABLE_TELEPORT_LIMIT
                            RootUtils.runAsRoot(command)
                            snackbarHostState.showSnackbar(if (isEnabled) "Teleport Anywhere Enabled." else "Teleport Anywhere Disabled.")
                        }
                    })
                }
            }
            item {
                TweakCard("Navigator Fog", "Enables the fog effect in the navigator background.") {
                    Switch(checked = isNavigatorFogEnabled, onCheckedChange = { isEnabled ->
                        isNavigatorFogEnabled = isEnabled
                        coroutineScope.launch {
                            val command = if (isEnabled) TweakCommands.ENABLE_NAVIGATOR_FOG else TweakCommands.DISABLE_NAVIGATOR_FOG
                            RootUtils.runAsRoot(command)
                            snackbarHostState.showSnackbar(if (isEnabled) "Navigator Fog Enabled." else "Navigator Fog Disabled.")
                        }
                    })
                }
            }
            item {
                TweakCard("Fixed Panel Scaling", "Makes panels change size with distance.") {
                    Switch(checked = isPanelScalingEnabled, onCheckedChange = { isEnabled ->
                        isPanelScalingEnabled = isEnabled
                        coroutineScope.launch {
                            val command = if (isEnabled) TweakCommands.ENABLE_PANEL_SCALING else TweakCommands.DISABLE_PANEL_SCALING
                            RootUtils.runAsRoot(command)
                            snackbarHostState.showSnackbar(if (isEnabled) "Panel Scaling Enabled." else "Panel Scaling Disabled.")
                        }
                    })
                }
            }
            item {
                TweakCard("Infinite Floating Panels", "Enables infinite floating panels") {
                    Switch(checked = isInfinitePanelsEnabled, onCheckedChange = { isEnabled ->
                        isInfinitePanelsEnabled = isEnabled
                        coroutineScope.launch {
                            val command = if (isEnabled) TweakCommands.ENABLE_INFINITE_PANELS else TweakCommands.DISABLE_INFINITE_PANELS
                            RootUtils.runAsRoot(command)
                            snackbarHostState.showSnackbar(if (isEnabled) "Infinite Panels Enabled." else "Infinite Panels Disabled.")
                        }
                    })
                }
            }
            item {
                TweakCard("Dogfood Hub", "Enables Dogfood Hub") {
                    Column(horizontalAlignment = Alignment.End) {
                        Switch(checked = isDogfoodEnabled, onCheckedChange = { isEnabled ->
                            restartDialogContent = if (isEnabled) {
                                Pair("This will restart your device's interface. After it reloads, please open this app again to complete the second step automatically.") {
                                    coroutineScope.launch {
                                        sharedPrefs.edit().putBoolean("dogfood_pending_step2", true).apply()
                                        RootUtils.runAsRoot(TweakCommands.ENABLE_DOGFOOD_STEP_1)
                                    }
                                }
                            } else {
                                Pair("This will disable the Dogfood Hub and restart your device's interface.") {
                                    coroutineScope.launch { RootUtils.runAsRoot(TweakCommands.DISABLE_DOGFOOD_HUB) }
                                }
                            }
                            showRestartDialog = true
                        })
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { coroutineScope.launch { RootUtils.runAsRoot(TweakCommands.LAUNCH_DOGFOOD_HUB) } },
                            enabled = isDogfoodEnabled
                        ) { Text("Launch") }
                    }
                }
            }
        }
    }
}

@Composable
fun TweakCard(title: String, description: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
            content()
        }
    }
}

object TweakCommands {
    const val LEDS_OFF = "echo 0 > /sys/class/leds/red/brightness\necho 0 > /sys/class/leds/green/brightness\necho 0 > /sys/class/leds/blue/brightness"
    val RGB_SCRIPT = """
#!/system/bin/sh
RED_LED="/sys/class/leds/red/brightness"
GREEN_LED="/sys/class/leds/green/brightness"
BLUE_LED="/sys/class/leds/blue/brightness"
set_rgb() { echo "${'$'}{1}" > "${'$'}RED_LED"; echo "${'$'}{2}" > "${'$'}GREEN_LED"; echo "${'$'}{3}" > "${'$'}BLUE_LED"; }
clamp() { if [ "${'$'}1" -lt 0 ]; then echo 0; elif [ "${'$'}1" -gt 255 ]; then echo 255; else echo "${'$'}1"; fi; }
trap "set_rgb 0 0 0; exit" INT TERM
while true; do
    for i in ${'$'}(seq 0 5 255); do set_rgb ${'$'}(clamp ${'$'}((255 - i))) ${'$'}(clamp ${'$'}{i}) 0; sleep 0.005; done
    for i in ${'$'}(seq 0 5 255); do set_rgb 0 ${'$'}(clamp ${'$'}((255 - i))) ${'$'}(clamp ${'$'}{i}); sleep 0.005; done
    for i in ${'$'}(seq 0 5 255); do set_rgb ${'$'}(clamp ${'$'}{i}) 0 ${'$'}(clamp ${'$'}((255 - i))); sleep 0.005; done
done
    """.trimIndent()
    const val ENABLE_DOGFOOD_STEP_1 = "magisk resetprop ro.build.type userdebug\nstop\nstart"
    const val ENABLE_DOGFOOD_STEP_2 = "am broadcast -a oculus.intent.action.DC_OVERRIDE --esa config_param_value oculus_systemshell:oculus_is_trusted_user:true\nstop\nstart"
    const val DISABLE_DOGFOOD_HUB = "magisk resetprop --delete ro.build.type\nstop\nstart"
    const val LAUNCH_DOGFOOD_HUB = "am start com.oculus.vrshell/com.oculus.panelapp.dogfood.DogfoodMainActivity"
    const val DISABLE_TELEPORT_LIMIT = "oculuspreferences --setc shell_teleport_anywhere true"
    const val ENABLE_TELEPORT_LIMIT = "oculuspreferences --setc shell_teleport_anywhere false"
    const val ENABLE_NAVIGATOR_FOG = "oculuspreferences --setc navigator_background_disabled false\nam force-stop com.oculus.vrshell"
    const val DISABLE_NAVIGATOR_FOG = "oculuspreferences --setc navigator_background_disabled true\nam force-stop com.oculus.vrshell"
    const val ENABLE_PANEL_SCALING = "oculuspreferences --setc panel_scaling true\nam force-stop com.oculus.vrshell"
    const val DISABLE_PANEL_SCALING = "oculuspreferences --setc panel_scaling false\nam force-stop com.oculus.vrshell"
    const val SET_UI_DOCK = "oculuspreferences --setc debug_navigator_state 0\nam force-stop com.oculus.vrshell"
    const val SET_UI_NAVIGATOR = "oculuspreferences --setc debug_navigator_state 1\nam force-stop com.oculus.vrshell"
    const val SET_TRANSITION_IMMERSIVE = "oculuspreferences --setc shell_immersive_transitions_enabled true\nam force-stop com.oculus.vrshell"
    const val SET_TRANSITION_VOID = "oculuspreferences --setc shell_immersive_transitions_enabled false\nam force-stop com.oculus.vrshell"
    const val ENABLE_INFINITE_PANELS = "oculuspreferences --setc debug_infinite_spatial_panels_enabled true\nam force-stop com.oculus.vrshell"
    const val DISABLE_INFINITE_PANELS = "oculuspreferences --setc debug_infinite_spatial_panels_enabled false\nam force-stop com.oculus.vrshell"
}

@Preview(showBackground = true, heightDp = 600)
@Composable
fun TweaksScreenPreview() {
    MaterialTheme {
        TweaksScreen()
    }
}