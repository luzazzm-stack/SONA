package app.sona.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sona.LibraryState
import app.sona.LibraryViewModel
import app.sona.core.Album
import app.sona.core.Artist
import app.sona.core.Folder
import app.sona.core.Song
import app.sona.playback.PlayerViewModel
import app.sona.ui.components.BottomBar
import app.sona.ui.components.MiniPlayer
import app.sona.ui.components.SonaTab
import app.sona.ui.screens.LibraryScreen
import app.sona.ui.screens.NowPlayingScreen
import app.sona.ui.screens.SearchScreen
import app.sona.ui.screens.SettingsScreen
import app.sona.ui.screens.SongCollectionScreen
import app.sona.ui.theme.BgBase

private sealed interface Detail {
    data class AlbumD(val album: Album) : Detail
    data class ArtistD(val artist: Artist) : Detail
    data class FolderD(val folder: Folder) : Detail
}

private data class DetailData(val title: String, val subtitle: String, val art: Uri?, val songs: List<Song>)

private fun detailData(d: Detail, state: LibraryState): DetailData = when (d) {
    is Detail.AlbumD -> {
        val songs = state.songs.filter { it.albumId == d.album.id }.sortedBy { it.track }
        DetailData(d.album.title, "${d.album.artist} · ${songs.size} songs", d.album.artworkUri, songs)
    }
    is Detail.ArtistD -> {
        val songs = state.songs.filter { it.artist == d.artist.name }
        DetailData(d.artist.name, "${songs.size} songs", songs.firstOrNull()?.artworkUri, songs)
    }
    is Detail.FolderD -> {
        val songs = state.songs.filter { it.path.substringBeforeLast('/') == d.folder.path }
        DetailData(d.folder.name, "${songs.size} songs", songs.firstOrNull()?.artworkUri, songs)
    }
}

@Composable
fun SonaRoot(libVm: LibraryViewModel, playerVm: PlayerViewModel) {
    val state by libVm.state.collectAsStateWithLifecycle()
    val playerUi by playerVm.ui.collectAsStateWithLifecycle()

    val tab = remember { mutableStateOf(SonaTab.Library) }
    val detail = remember { mutableStateOf<Detail?>(null) }
    val nowPlaying = remember { mutableStateOf(false) }

    val curId = playerUi.current?.id
    val isPlaying = playerUi.isPlaying

    // Stable callbacks so the song-list screens stay skippable across the 500ms position ticks.
    val play = remember(playerVm) { { list: List<Song>, i: Int -> playerVm.playQueue(list, i) } }
    val shuffleAll = remember(playerVm) { { list: List<Song> -> playerVm.playShuffled(list) } }
    val toggleFav = remember(libVm) { { id: Long -> libVm.toggleFavorite(id) } }
    val openAlbum = remember { { a: Album -> detail.value = Detail.AlbumD(a) } }
    val openArtist = remember { { a: Artist -> detail.value = Detail.ArtistD(a) } }
    val openFolder = remember { { f: Folder -> detail.value = Detail.FolderD(f) } }

    if (nowPlaying.value) BackHandler { nowPlaying.value = false }
    else if (detail.value != null) BackHandler { detail.value = null }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Box(Modifier.fillMaxSize()) {
            val d = detail.value
            if (d != null) {
                val dd = detailData(d, state)
                SongCollectionScreen(
                    title = dd.title, subtitle = dd.subtitle, artworkUri = dd.art, songs = dd.songs,
                    curId = curId, isPlaying = isPlaying, favorites = state.favorites,
                    onBack = { detail.value = null },
                    onPlay = { if (dd.songs.isNotEmpty()) play(dd.songs, 0) },
                    onShuffle = { if (dd.songs.isNotEmpty()) shuffleAll(dd.songs) },
                    onPlaySong = play, onToggleFav = toggleFav,
                )
            } else when (tab.value) {
                SonaTab.Library -> LibraryScreen(
                    state, curId, isPlaying, play, shuffleAll, openAlbum, openArtist, openFolder, toggleFav,
                )
                SonaTab.Search -> SearchScreen(state, curId, isPlaying, play, toggleFav)
                SonaTab.Settings -> SettingsScreen(state.songs.size) { libVm.load() }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (playerUi.hasCurrent) {
                MiniPlayer(playerUi, onTap = { nowPlaying.value = true }, onPlayPause = { playerVm.togglePlay() }, onNext = { playerVm.next() })
            }
            BottomBar(tab.value) { tab.value = it; detail.value = null }
        }

        if (nowPlaying.value && playerUi.hasCurrent) {
            val fav = curId != null && curId in state.favorites
            NowPlayingScreen(
                ui = playerUi, isFavorite = fav,
                onClose = { nowPlaying.value = false },
                onPlayPause = { playerVm.togglePlay() },
                onNext = { playerVm.next() }, onPrev = { playerVm.prev() },
                onSeek = { playerVm.seekTo(it) },
                onToggleShuffle = { playerVm.toggleShuffle() },
                onCycleRepeat = { playerVm.cycleRepeat() },
                onToggleFav = toggleFav,
            )
        }
    }
}
