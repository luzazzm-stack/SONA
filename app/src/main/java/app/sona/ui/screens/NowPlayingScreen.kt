package app.sona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sona.core.SonaRepeat
import app.sona.core.asDuration
import app.sona.playback.PlayerUi
import app.sona.ui.components.AlbumArt
import app.sona.ui.theme.Accent
import app.sona.ui.theme.BgBase
import app.sona.ui.theme.BorderSubtle
import app.sona.ui.theme.IconDefault
import app.sona.ui.theme.OnAccent
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary
import coil.compose.AsyncImage

@Composable
fun NowPlayingScreen(
    ui: PlayerUi,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFav: (Long) -> Unit,
) {
    val song = ui.current
    if (song == null) {
        Box(Modifier.fillMaxSize().background(BgBase))
        return
    }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val progress = when {
        scrubbing -> scrubValue
        ui.durationMs > 0 -> (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownPos = if (scrubbing) (scrubValue * ui.durationMs).toLong() else ui.positionMs

    Box(Modifier.fillMaxSize().background(BgBase)) {
        AsyncImage(
            model = song.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(55.dp),
        )
        Box(Modifier.matchParentSize().background(Color(0xD6000000)))

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "close", tint = TextPrimary, modifier = Modifier.size(30.dp))
                }
                Text("NOW PLAYING", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.size(44.dp))
            }

            Spacer(Modifier.weight(1f))
            AlbumArt(song.artworkUri, song.title, Modifier.fillMaxWidth(0.82f).aspectRatio(1f), 16.dp)
            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(48.dp).clickable { onToggleFav(song.id) }, contentAlignment = Alignment.Center) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "favorite", tint = if (isFavorite) Accent else IconDefault, modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = progress,
                onValueChange = { scrubbing = true; scrubValue = it },
                onValueChangeFinished = { onSeek((scrubValue * ui.durationMs).toLong()); scrubbing = false },
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = BorderSubtle),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(shownPos.asDuration(), color = TextMuted, fontSize = 11.sp)
                Text(ui.durationMs.asDuration(), color = TextMuted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clickable { onToggleShuffle() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Shuffle, "shuffle", tint = if (ui.shuffle) Accent else IconDefault, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.size(48.dp).clickable { onPrev() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SkipPrevious, "previous", tint = TextPrimary, modifier = Modifier.size(34.dp))
                }
                Box(Modifier.size(72.dp).clip(CircleShape).background(Accent).clickable { onPlayPause() }, contentAlignment = Alignment.Center) {
                    Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "play/pause", tint = OnAccent, modifier = Modifier.size(36.dp))
                }
                Box(Modifier.size(48.dp).clickable { onNext() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SkipNext, "next", tint = TextPrimary, modifier = Modifier.size(34.dp))
                }
                Box(Modifier.size(48.dp).clickable { onCycleRepeat() }, contentAlignment = Alignment.Center) {
                    Icon(
                        if (ui.repeat == SonaRepeat.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "repeat", tint = if (ui.repeat != SonaRepeat.OFF) Accent else IconDefault, modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
