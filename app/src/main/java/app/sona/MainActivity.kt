package app.sona

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
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
        setContent {
            SonaTheme {
                val context = LocalContext.current
                val libVm: LibraryViewModel = viewModel()
                val playerVm: PlayerViewModel = viewModel()
                var granted by remember { mutableStateOf(AudioPermission.granted(context)) }

                val audioLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { ok -> granted = ok }

                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

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
                    PermissionScreen { audioLauncher.launch(AudioPermission.name()) }
                }
            }
        }
    }
}
