package app.sona.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.sona.ui.theme.Accent
import app.sona.ui.theme.OnAccent
import app.sona.ui.theme.Surface1
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary

@Composable
fun SettingsScreen(songCount: Int, onRescan: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check system state when returning from Settings pages.
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val batteryExempt = remember(refresh) {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val notifDenied = remember(refresh) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Card {
            SettingRow("Rescan library", "$songCount songs found", onClick = onRescan)
        }

        Text("Playback", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Card {
            Column(Modifier.fillMaxWidth()) {
                LinkRow(
                    title = "Unrestricted background playback",
                    subtitle = if (batteryExempt) "Battery optimization is off for SONA"
                    else "Stops playback dying mid-session — tap to allow",
                    status = if (batteryExempt) "On" else "Off",
                    statusColor = if (batteryExempt) Accent else TextMuted,
                    onClick = if (batteryExempt) null else ({
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }.onFailure {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            }
                        }
                    }),
                )
                LinkRow(
                    title = "Allow auto-launch",
                    subtitle = "Realme/ColorOS also needs \"Allow auto-launch\" in app settings — tap to open",
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                        )
                    }
                }
                if (notifDenied) {
                    LinkRow(
                        title = "Media notification is off",
                        subtitle = "Lock-screen controls unavailable — tap to turn on",
                    ) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    }
                }
            }
        }

        Card {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Accent", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("SONA red · locked", color = TextMuted, fontSize = 11.sp)
                }
                Box(Modifier.size(22.dp).clip(CircleShape).background(Accent))
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("SONA v0.1.3", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(6.dp))
                Text("An offline, local music player.", color = TextSecondary, fontSize = 12.sp)
                Text("No ads. No tracking. No network.", color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1)) { content() }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Text("Scan", color = OnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Accent).padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    status: String? = null,
    statusColor: Color = TextMuted,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            Text(status, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
