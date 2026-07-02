package app.sona

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import app.sona.ui.theme.SonaTheme

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

                LaunchedEffect(granted) {
                    if (granted) {
                        libVm.load()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
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
