package app.sona.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sona.core.Song
import app.sona.ui.components.AlbumArt
import app.sona.ui.components.SongRow
import app.sona.ui.theme.Accent
import app.sona.ui.theme.BorderSubtle
import app.sona.ui.theme.OnAccent
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary

@Composable
fun SongCollectionScreen(
    title: String,
    subtitle: String,
    artworkUri: Uri?,
    songs: List<Song>,
    curId: Long?,
    isPlaying: Boolean,
    favorites: Set<Long>,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onToggleFav: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, "back", tint = TextPrimary, modifier = Modifier.size(24.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 150.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AlbumArt(artworkUri, title, Modifier.size(180.dp), 20.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(Accent).clickable { onPlay() }.padding(horizontal = 22.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = OnAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play", color = OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(BorderSubtle).clickable { onShuffle() }.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Shuffle, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Shuffle", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                SongRow(s, s.id == curId, isPlaying, s.id in favorites, { onPlaySong(songs, i) }, { onToggleFav(s.id) })
            }
        }
    }
}
