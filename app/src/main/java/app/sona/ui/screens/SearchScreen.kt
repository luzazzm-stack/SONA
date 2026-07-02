package app.sona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sona.LibraryState
import app.sona.core.Song
import app.sona.ui.components.SongRow
import app.sona.ui.theme.Accent
import app.sona.ui.theme.Surface2
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary

@Composable
fun SearchScreen(
    state: LibraryState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (List<Song>, Int) -> Unit,
    onToggleFav: (Long) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val results = remember(q, state.songs) {
        if (q.isBlank()) emptyList()
        else state.songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }
    val curId = currentSongId

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth().background(Surface2, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = TextMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Songs, artists, albums", color = TextMuted, fontSize = 14.sp)
                BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (q.isNotBlank() && results.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No results for \"$q\"", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 150.dp)) {
                itemsIndexed(results, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, s.id == curId, isPlaying, s.id in state.favorites, { onPlaySong(results, i) }, { onToggleFav(s.id) })
                }
            }
        }
    }
}
