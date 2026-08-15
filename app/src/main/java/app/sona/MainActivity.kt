package app.sona

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sona.core.AudioPermission
import app.sona.playback.PlayerViewModel
import app.sona.ui.SonaRoot
import app.sona.ui.screens.PermissionScreen
import app.sona.ui.theme.Accent
import app.sona.ui.theme.SonaTheme
import app.sona.ui.theme.Surface2
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val activity = this
        setContent {
            SonaTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val libVm: LibraryViewModel = viewModel()
                val playerVm: PlayerViewModel = viewModel()
                var granted by remember { mutableStateOf(AudioPermission.granted(context)) }
                var asked by remember { mutableStateOf(false) }

                val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                    granted = ok; asked = true
                }
                val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

                // Re-check when returning to the app (e.g. permission granted via system Settings).
                DisposableEffect(lifecycleOwner) {
                    val obs = LifecycleEventObserver { _, e ->
                        if (e == Lifecycle.Event.ON_RESUME) granted = AudioPermission.granted(context)
                    }
                    lifecycleOwner.lifecycle.addObserver(obs)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
                }

                var showBatteryPrompt by remember { mutableStateOf(false) }

                LaunchedEffect(granted) {
                    if (granted) {
                        libVm.load()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        // One-time nudge: ColorOS-style battery optimization kills long playback sessions.
                        val prefs = context.getSharedPreferences("sona_prefs", Context.MODE_PRIVATE)
                        val exempt = context.getSystemService(PowerManager::class.java)
                            ?.isIgnoringBatteryOptimizations(context.packageName) == true
                        if (!exempt && !prefs.getBoolean("battery_prompt_done", false)) showBatteryPrompt = true
                    }
                }

                if (showBatteryPrompt) {
                    val dismiss = {
                        context.getSharedPreferences("sona_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("battery_prompt_done", true).apply()
                        showBatteryPrompt = false
                    }
                    AlertDialog(
                        onDismissRequest = dismiss,
                        containerColor = Surface2,
                        title = { Text("Keep music playing", color = TextPrimary) },
                        text = {
                            Text(
                                "Your phone's battery optimization can stop playback during long sessions. Allow SONA unrestricted background playback?",
                                color = TextSecondary,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                dismiss()
                                runCatching {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:$packageName"),
                                        )
                                    )
                                }.onFailure {
                                    runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                                }
                            }) { Text("Allow", color = Accent) }
                        },
                        dismissButton = { TextButton(onClick = dismiss) { Text("Not now", color = TextMuted) } },
                    )
                }

                if (granted) {
                    SonaRoot(libVm, playerVm)
                } else {
                    PermissionScreen {
                        val permanentlyDenied = asked &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(activity, AudioPermission.name())
                        if (permanentlyDenied) {
                            activity.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", activity.packageName, null),
                                )
                            )
                        } else {
                            audioLauncher.launch(AudioPermission.name())
                        }
                    }
                }
            }
        }
    }
}
