package app.sona.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sona.playback.PlayerUi
import app.sona.ui.theme.Accent
import app.sona.ui.theme.DividerC
import app.sona.ui.theme.IconDefault
import app.sona.ui.theme.NavBg
import app.sona.ui.theme.Surface2
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary

enum class SonaTab(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Rounded.LibraryMusic),
    Search("Search", Icons.Rounded.Search),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
fun MiniPlayer(
    ui: PlayerUi,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Remember the last non-null song so the bar can still draw itself while its exit animation
    // runs after playback is cleared (ui.current -> null). Contract unchanged: renders nothing if
    // no song was ever set. Display always prefers the live ui.current when present.
    val current = ui.current
    var lastSong by remember { mutableStateOf(current) }
    SideEffect { if (current != null && current != lastSong) lastSong = current }
    val song = current ?: lastSong ?: return
    val progress = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier.fillMaxWidth().background(Surface2)) {
        // red progress hairline
        Box(Modifier.fillMaxWidth().height(2.dp).background(DividerC)) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(Accent))
        }
        Row(
            Modifier.fillMaxWidth().height(62.dp).clickable { onTap() }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArt(song.artworkUri, song.title, Modifier.size(46.dp), 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(48.dp).clickable { onPlayPause() }, contentAlignment = Alignment.Center) {
                Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "play/pause", tint = Accent, modifier = Modifier.size(26.dp))
            }
            Box(Modifier.size(48.dp).clickable { onNext() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SkipNext, "next", tint = IconDefault, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun BottomBar(current: SonaTab, modifier: Modifier = Modifier, onSelect: (SonaTab) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .background(NavBg)
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SonaTab.entries.forEach { tab ->
            val active = tab == current
            Column(
                Modifier.clickable { onSelect(tab) }.padding(horizontal = 18.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, tab.label, tint = if (active) Accent else TextMuted, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(3.dp))
                Text(tab.label, color = if (active) Accent else TextMuted, fontSize = 10.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
