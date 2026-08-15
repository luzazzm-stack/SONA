package app.sona.ui.components

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sona.core.Song
import app.sona.core.asDuration
import app.sona.ui.theme.Accent
import app.sona.ui.theme.AccentRow
import app.sona.ui.theme.IconMuted
import app.sona.ui.theme.ScrimSheet
import app.sona.ui.theme.Surface2
import app.sona.ui.theme.Surface3
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary
import coil.compose.AsyncImage

private fun monogram(s: String): String = s.trim().firstOrNull()?.uppercase() ?: "♪"

/** Album artwork with a greyscale monogram fallback (only place non-red color lives — inside real art). */
@Composable
fun AlbumArt(uri: Uri?, seed: String, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Surface3, Surface2))),
        contentAlignment = Alignment.Center,
    ) {
        Text(monogram(seed), color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}

/** Three animated red bars — the "now playing" indicator. */
@Composable
fun PlayingBars(modifier: Modifier = Modifier, playing: Boolean = true, color: Color = Accent) {
    val heights: List<Float> = if (playing) {
        val t = rememberInfiniteTransition(label = "eq")
        val h1 by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(340), RepeatMode.Reverse), label = "b1")
        val h2 by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "b2")
        val h3 by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(460), RepeatMode.Reverse), label = "b3")
        listOf(h1, h2, h3)
    } else {
        listOf(0.4f, 0.4f, 0.4f)
    }
    Row(modifier.height(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        heights.forEach { hv ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(hv)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) AccentRow else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            AlbumArt(song.artworkUri, song.title, Modifier.size(48.dp), 8.dp)
            if (isCurrent) {
                Box(Modifier.matchParentSize().background(ScrimSheet, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    PlayingBars(playing = isPlaying)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) Accent else TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(song.durationMs.asDuration(), color = TextMuted, fontSize = 11.sp)
        // 48dp touch target around the 20dp glyph — a heart mis-tap must never start playback.
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(50)).clickable { onToggleFav() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "favorite",
                tint = if (isFavorite) Accent else IconMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
