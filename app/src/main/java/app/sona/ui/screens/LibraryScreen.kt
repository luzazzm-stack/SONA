package app.sona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sona.LibraryState
import app.sona.core.Album
import app.sona.core.Artist
import app.sona.core.Folder
import app.sona.core.Song
import app.sona.playback.PlayerUi
import app.sona.ui.components.AlbumArt
import app.sona.ui.components.SongRow
import app.sona.ui.theme.Accent
import app.sona.ui.theme.Surface2
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary

private val TABS = listOf("Songs", "Albums", "Artists", "Folders", "Favorites")
private val listPad = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 150.dp)

@Composable
fun LibraryScreen(
    state: LibraryState,
    playerUi: PlayerUi,
    onPlaySong: (List<Song>, Int) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onToggleFav: (Long) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val curId = playerUi.current?.id

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SONA", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TABS.forEachIndexed { i, label ->
                val active = i == tab
                Text(
                    label,
                    color = if (active) TextPrimary else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (active) Accent.copy(alpha = 0.14f) else Surface2)
                        .clickable { tab = i }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> SongList(state.songs, curId, playerUi.isPlaying, state.favorites, onPlaySong, onToggleFav)
                1 -> AlbumGrid(state.albums, onOpenAlbum)
                2 -> ArtistList(state.artists, onOpenArtist)
                3 -> FolderList(state.folders, onOpenFolder)
                else -> {
                    if (state.favoriteSongs.isEmpty()) EmptyHint("No favorites yet — tap the heart on any song.")
                    else SongList(state.favoriteSongs, curId, playerUi.isPlaying, state.favorites, onPlaySong, onToggleFav)
                }
            }
            if (tab == 0 && state.loaded && state.songs.isEmpty()) EmptyHint("No music found on this device.")
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>, curId: Long?, isPlaying: Boolean, favorites: Set<Long>,
    onPlaySong: (List<Song>, Int) -> Unit, onToggleFav: (Long) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPad) {
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(
                song = s,
                isCurrent = s.id == curId,
                isPlaying = isPlaying,
                isFavorite = s.id in favorites,
                onClick = { onPlaySong(songs, i) },
                onToggleFav = { onToggleFav(s.id) },
            )
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = listPad, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        gridItems(albums, key = { it.id }) { a ->
            Column(Modifier.clickable { onOpen(a) }) {
                AlbumArt(a.artworkUri, a.title, Modifier.fillMaxWidth().aspectRatio(1f), 12.dp)
                Spacer(Modifier.height(8.dp))
                Text(a.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.artist, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onOpen: (Artist) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPad) {
        items(artists, key = { it.name }) { ar ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(ar) }.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(Surface2), contentAlignment = Alignment.Center) {
                    Text(ar.name.firstOrNull()?.uppercase() ?: "?", color = TextMuted, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ar.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${ar.songCount} songs · ${ar.albumCount} albums", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun FolderList(folders: List<Folder>, onOpen: (Folder) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPad) {
        items(folders, key = { it.path }) { f ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(f) }.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, tint = TextMuted, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${f.songCount} songs", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextMuted, fontSize = 13.sp)
    }
}
