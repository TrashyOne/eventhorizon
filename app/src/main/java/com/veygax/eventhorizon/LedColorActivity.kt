package com.veygax.eventhorizon

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.io.File

class LedColorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LedColorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedColorScreen() {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity
    val sharedPrefs = remember { context.getSharedPreferences("eventhorizon_prefs", Context.MODE_PRIVATE) }
    val scriptFile = remember { File(context.filesDir, "custom_led.sh") }

    var red by remember { mutableStateOf(sharedPrefs.getInt("led_red", 255).toFloat()) }
    var green by remember { mutableStateOf(sharedPrefs.getInt("led_green", 255).toFloat()) }
    var blue by remember { mutableStateOf(sharedPrefs.getInt("led_blue", 255).toFloat()) }
    var setOnBoot by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("custom_led_on_boot", false)) }

    val currentColor = Color(red.toInt(), green.toInt(), blue.toInt())

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                setOnBoot = sharedPrefs.getBoolean("custom_led_on_boot", false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Custom LED Color") },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(color = currentColor, shape = MaterialTheme.shapes.medium)
            )

            ColorSlider(label = "Red", value = red, onValueChange = { red = it }, color = Color.Red)
            ColorSlider(label = "Green", value = green, onValueChange = { green = it }, color = Color.Green)
            ColorSlider(label = "Blue", value = blue, onValueChange = { blue = it }, color = Color.Blue)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Set on Boot", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = setOnBoot, onCheckedChange = { isEnabled ->
                    setOnBoot = isEnabled
                    val editor = sharedPrefs.edit()
                    editor.putBoolean("custom_led_on_boot", isEnabled)
                    if (isEnabled) {
                        editor.putBoolean("rgb_on_boot", false)
                    }
                    editor.apply()
                })
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    coroutineScope.launch {
                        sharedPrefs.edit()
                            .putInt("led_red", red.toInt())
                            .putInt("led_green", green.toInt())
                            .putInt("led_blue", blue.toInt())
                            .putBoolean("custom_led_active", true)
                            .apply()

                        val customColorScript = """
                            #!/system/bin/sh
                            pkill -f rgb_led.sh
                            
                            RED_LED="/sys/class/leds/red/brightness"
                            GREEN_LED="/sys/class/leds/green/brightness"
                            BLUE_LED="/sys/class/leds/blue/brightness"
                            
                            while true; do
                                echo ${red.toInt()} > "${'$'}RED_LED"
                                echo ${green.toInt()} > "${'$'}GREEN_LED"
                                echo ${blue.toInt()} > "${'$'}BLUE_LED"
                                sleep 1
                            done
                        """.trimIndent()

                        scriptFile.writeText(customColorScript)
                        RootUtils.runAsRoot("chmod +x ${scriptFile.absolutePath}")
                        RootUtils.runAsRoot("pkill -f custom_led.sh")
                        RootUtils.runAsRoot("${scriptFile.absolutePath} &")

                        snackbarHostState.showSnackbar("Custom color set!")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Color")
            }
        }
    }
}

@Composable
fun ColorSlider(label: String, value: Float, onValueChange: (Float) -> Unit, color: Color) {
    Column {
        Text(text = "$label: ${value.toInt()}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            steps = 254,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
    }
}