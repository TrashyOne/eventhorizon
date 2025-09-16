package com.veygax.eventhorizon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startDnsService()
        }
    }

    private fun startDnsService() {
        val intent = Intent(this, DnsBlockerService::class.java).setAction(DnsBlockerService.ACTION_START)
        startService(intent)
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startDnsService()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
                    EventHorizonApp(
                        autoRootOnStart = intent?.getBooleanExtra("auto_root", false) ?: false,
                        autoStartDnsBlocker = intent?.getBooleanExtra("start_dns_blocker", false) ?: false,
                        onRequestVpnPermission = { requestVpnPermission() }
                    )
                }
            }
        }
    }

    fun isPatched(): Boolean {
        val lastVersion = lastVersionForDevice()
        if (lastVersion == 0L) {
            return false // you're on your own
        }
        return getVersionIncremental() > lastVersion
    }

    private fun lastVersionForDevice(): Long = when(Build.BOARD) {
        "eureka" -> 51154110129000520L
        "panther" -> 1176880099000610L
        else -> 0
    }

    private fun getVersionIncremental() = Build.VERSION.INCREMENTAL.toLong()

    @RequiresApi(Build.VERSION_CODES.O)
    fun executeExploit(
        context: Context,
        onOutput: (String) -> Unit,
        onProcessComplete: () -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val assetManager = assets
                val extractedDir = getDir("exploit", 0)

                for (filename in assetManager.list("exploit")!!) {
                    val targetFile = File(extractedDir, filename)
                    assetManager.open("exploit/$filename").use { inputStream ->
                        Files.copy(
                            inputStream,
                            targetFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        targetFile.setExecutable(true)
                    }
                }

                val executablePath = applicationInfo.nativeLibraryDir + "/libexploit.so"
                val launchShPath = File(extractedDir, "launch.sh").path
                val processBuilder = ProcessBuilder()
                    .command(executablePath, "sh", launchShPath)
                    .redirectErrorStream(true)
                val process = processBuilder.start()

                launch {
                    process.inputStream.toLineFlow()
                        .collect { line ->
                            launch(Dispatchers.Main) {
                                onOutput(line)
                            }
                        }
                }

                process.waitFor()

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    onOutput("Error: ${e.message}")
                }
            } finally {
                launch(Dispatchers.Main) {
                    onProcessComplete()
                }
            }
        }
    }

    private fun InputStream.toLineFlow() = bufferedReader(StandardCharsets.UTF_8)
        .lineSequence()
        .asFlow()
        .onCompletion { close() }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventHorizonApp(
    autoRootOnStart: Boolean,
    autoStartDnsBlocker: Boolean,
    onRequestVpnPermission: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("eventhorizon_prefs", Context.MODE_PRIVATE) }

    var rootOnBoot by remember { mutableStateOf(sharedPrefs.getBoolean("root_on_boot", false)) }
    var consoleText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    var isProcessRunning by remember { mutableStateOf(false) }
    var isRooted by remember { mutableStateOf(false) }

    val mainActivity = (LocalContext.current as MainActivity)

    LaunchedEffect(Unit) {
        isRooted = RootUtils.isRootAvailable()
    }

    LaunchedEffect(autoRootOnStart) {
        if (autoRootOnStart && !isProcessRunning) {
            mainActivity.executeExploit(
                context,
                onOutput = { line -> consoleText += line + "\n" },
                onProcessComplete = { isProcessRunning = false }
            )
            isProcessRunning = true
        }
    }

    // New effect to handle starting the DNS blocker on boot
    LaunchedEffect(autoStartDnsBlocker) {
        if (autoStartDnsBlocker) {
            onRequestVpnPermission()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "eventhorizon",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Left,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "root for the meta surveillance device by veygax, zhuowei and freexr",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Left,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        ListItem(
            headlineContent = { Text("Root Status") },
            supportingContent = {
                Text(if (isRooted) "Root Access Granted" else "Root not available")
            },
            leadingContent = {
                Icon(
                    imageVector = if (isRooted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Root Status Icon",
                    tint = if (isRooted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        )

        ListItem(
            headlineContent = { Text("Root on Boot") },
            supportingContent = {
                Text(if (rootOnBoot) "Root on Startup" else "Won't Root on Startup")
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Power,
                    contentDescription = "Power"
                )
            },
            trailingContent = {
                Switch(
                    checked = rootOnBoot,
                    onCheckedChange = { checked ->
                        rootOnBoot = checked
                        sharedPrefs.edit().putBoolean("root_on_boot", checked).apply()
                    }
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (mainActivity.isPatched()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ exploit patched on this device",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "the exploit will NOT work.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                if (!isProcessRunning) {
                    mainActivity.executeExploit(context, onOutput = { line ->
                        consoleText += line + "\n"
                    }, onProcessComplete = {
                        isProcessRunning = false
                    })
                    isProcessRunning = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessRunning && !isRooted
        ) {
            Text(if (isProcessRunning) "Rooting..." else "Root Now")
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isRooted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val intent = Intent(context, TweaksActivity::class.java)
                        intent.putExtra("is_rooted", true)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("AIO Tweaks")
                }
                Button(
                    onClick = {
                        val intent = Intent(context, AppsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apps")
                }
            }
        } else {
            Button(
                onClick = {
                    val intent = Intent(context, TweaksActivity::class.java)
                    intent.putExtra("is_rooted", false)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("AIO Tweaks")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Log Output:",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            SelectionContainer {
                Text(
                    text = if (consoleText.isEmpty()) "no output yet..." else consoleText,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(scrollState),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        LaunchedEffect(consoleText) {
            if (consoleText.isNotEmpty()) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
}