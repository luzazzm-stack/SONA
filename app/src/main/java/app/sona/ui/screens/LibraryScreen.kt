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
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sona.LibraryState
import app.sona.LibraryViewModel
import app.sona.core.Album
import app.sona.core.Artist
import app.sona.core.Folder
import app.sona.core.Song
import app.sona.core.SortMode
import app.sona.ui.components.AlbumArt
import app.sona.ui.components.ShimmerHost
import app.sona.ui.components.SkeletonRow
import app.sona.ui.components.SongRow
import app.sona.ui.theme.Accent
import app.sona.ui.theme.Surface2
import app.sona.ui.theme.Surface3
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary

private val TABS = listOf("Songs", "Albums", "Artists", "Folders", "Favorites")
private val listPad = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 150.dp)

private fun sortSongs(songs: List<Song>, mode: SortMode): List<Song> = when (mode) {
    SortMode.Title -> songs.sortedBy { it.title.lowercase() }
    SortMode.Artist -> songs.sortedBy { it.artist.lowercase() + it.title.lowercase() }
    SortMode.Album -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
    SortMode.Recent -> songs.sortedByDescending { it.dateAdded }
    SortMode.Duration -> songs.sortedBy { it.durationMs }
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    currentSongId: Long?,
    isPlaying: Boolean,
    onPlaySong: (List<Song>, Int) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onToggleFav: (Long) -> Unit,
) {
    val libVm: LibraryViewModel = viewModel() // activity-scoped: same instance MainActivity created
    val tab = state.tab
    val sortMode = state.sort
    var sortMenu by remember { mutableStateOf(false) }
    val curId = currentSongId

    val sortedSongs = remember(state.songs, sortMode) { sortSongs(state.songs, sortMode) }
    val sortedFavs = remember(state.favorites, state.songs, sortMode) { sortSongs(state.favoriteSongs, sortMode) }
    val shuffleTarget = if (tab == 4) sortedFavs else sortedSongs

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header: wordmark + shuffle-all + sort
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SONA", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(Accent))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(44.dp).clickable { onShuffleAll(shuffleTarget) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Shuffle, "Shuffle all", tint = Accent, modifier = Modifier.size(22.dp))
            }
            Box {
                Box(Modifier.size(44.dp).clickable { sortMenu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SwapVert, "Sort", tint = TextPrimary, modifier = Modifier.size(24.dp))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortMode.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label, color = if (m == sortMode) Accent else TextPrimary, fontWeight = if (m == sortMode) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { libVm.setSort(m); sortMenu = false },
                        )
                    }
                }
            }
        }

        // Tabs
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
                        .clickable { libVm.setTab(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // Count + sort strip (visible without selecting anything)
        val stripText = when (tab) {
            0 -> "${sortedSongs.size} songs · ${sortMode.label}"
            1 -> "${state.albums.size} albums"
            2 -> "${state.artists.size} artists"
            3 -> "${state.folders.size} folders"
            else -> "${sortedFavs.size} favorites · ${sortMode.label}"
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stripText, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (tab == 0 || tab == 4) {
                Text(
                    "Sort",
                    color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Surface3).clickable { sortMenu = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!state.loaded) {
                ShimmerHost {
                    Column(Modifier.fillMaxSize().padding(listPad)) { repeat(8) { SkeletonRow() } }
                }
            } else when (tab) {
                0 -> if (sortedSongs.isEmpty()) EmptyHint("No music found on this device.")
                    else SongList(sortedSongs, curId, isPlaying, state.favorites, onPlaySong, onToggleFav)
                1 -> if (state.albums.isEmpty()) EmptyHint("No albums found") else AlbumGrid(state.albums, onOpenAlbum)
                2 -> if (state.artists.isEmpty()) EmptyHint("No artists found") else ArtistList(state.artists, onOpenArtist)
                3 -> if (state.folders.isEmpty()) EmptyHint("No folders found") else FolderList(state.folders, onOpenFolder)
                else -> if (sortedFavs.isEmpty()) EmptyHint("No favorites yet — tap the heart on any song.")
                    else SongList(sortedFavs, curId, isPlaying, state.favorites, onPlaySong, onToggleFav)
            }
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
