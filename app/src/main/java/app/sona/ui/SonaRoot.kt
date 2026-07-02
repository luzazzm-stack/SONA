package app.sona.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val state by libVm.state.collectAsState()
    val playerUi by playerVm.ui.collectAsState()

    var tab by remember { mutableStateOf(SonaTab.Library) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var nowPlaying by remember { mutableStateOf(false) }

    val play: (List<Song>, Int) -> Unit = { list, i -> playerVm.playQueue(list, i) }
    val curId = playerUi.current?.id

    if (nowPlaying) BackHandler { nowPlaying = false }
    else if (detail != null) BackHandler { detail = null }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(BgBase)) {
        // Main content
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            val d = detail
            if (d != null) {
                val dd = detailData(d, state)
                SongCollectionScreen(
                    title = dd.title, subtitle = dd.subtitle, artworkUri = dd.art, songs = dd.songs,
                    curId = curId, isPlaying = playerUi.isPlaying, favorites = state.favorites,
                    onBack = { detail = null },
                    onPlay = { if (dd.songs.isNotEmpty()) play(dd.songs, 0) },
                    onShuffle = { if (dd.songs.isNotEmpty()) { play(dd.songs, 0); playerVm.toggleShuffle() } },
                    onPlaySong = play, onToggleFav = libVm::toggleFavorite,
                )
            } else when (tab) {
                SonaTab.Library -> LibraryScreen(
                    state, playerUi, play,
                    onShuffleAll = { playerVm.playShuffled(it) },
                    onOpenAlbum = { detail = Detail.AlbumD(it) },
                    onOpenArtist = { detail = Detail.ArtistD(it) },
                    onOpenFolder = { detail = Detail.FolderD(it) },
                    onToggleFav = libVm::toggleFavorite,
                )
                SonaTab.Search -> SearchScreen(state, playerUi, play, libVm::toggleFavorite)
                SonaTab.Settings -> SettingsScreen(state.songs.size) { libVm.load() }
            }
        }

        // Bottom: mini-player + nav
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (playerUi.hasCurrent) {
                MiniPlayer(playerUi, onTap = { nowPlaying = true }, onPlayPause = playerVm::togglePlay, onNext = playerVm::next)
            }
            BottomBar(tab) { tab = it; detail = null }
        }

        // Now Playing overlay
        if (nowPlaying && playerUi.hasCurrent) {
            val fav = curId != null && curId in state.favorites
            NowPlayingScreen(
                ui = playerUi, isFavorite = fav,
                onClose = { nowPlaying = false },
                onPlayPause = playerVm::togglePlay,
                onNext = playerVm::next, onPrev = playerVm::prev,
                onSeek = playerVm::seekTo,
                onToggleShuffle = playerVm::toggleShuffle,
                onCycleRepeat = playerVm::cycleRepeat,
                onToggleFav = libVm::toggleFavorite,
            )
        }
    }
}
