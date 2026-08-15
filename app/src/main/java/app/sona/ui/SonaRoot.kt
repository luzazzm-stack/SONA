package app.sona.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sona.LibraryState
import app.sona.LibraryViewModel
import app.sona.core.Album
import app.sona.core.Artist
import app.sona.core.Folder
import app.sona.core.Song
import app.sona.playback.PlayerViewModel
import app.sona.ui.components.AnimSpecs
import app.sona.ui.components.AnimatedOverlay
import app.sona.ui.components.BottomBar
import app.sona.ui.components.MiniPlayer
import app.sona.ui.components.SonaTab
import app.sona.ui.screens.LibraryScreen
import app.sona.ui.screens.NowPlayingScreen
import app.sona.ui.screens.SearchScreen
import app.sona.ui.screens.SettingsScreen
import app.sona.ui.screens.SongCollectionScreen
import app.sona.ui.theme.BgBase

// Detail holds parcel-friendly KEYS (not the model objects) so it can survive process death via
// rememberSaveable; everything displayable is re-derived from LibraryState in detailData().
private sealed interface Detail {
    data class AlbumD(val id: Long) : Detail
    data class ArtistD(val name: String) : Detail
    data class FolderD(val path: String) : Detail
}

private val DetailSaver = listSaver<Detail?, String>(
    save = { d ->
        when (d) {
            null -> emptyList()
            is Detail.AlbumD -> listOf("album", d.id.toString())
            is Detail.ArtistD -> listOf("artist", d.name)
            is Detail.FolderD -> listOf("folder", d.path)
        }
    },
    restore = { l ->
        when (l.firstOrNull()) {
            "album" -> l.getOrNull(1)?.toLongOrNull()?.let { Detail.AlbumD(it) }
            "artist" -> l.getOrNull(1)?.let { Detail.ArtistD(it) }
            "folder" -> l.getOrNull(1)?.let { Detail.FolderD(it) }
            else -> null
        }
    },
)

private data class DetailData(val title: String, val subtitle: String, val art: Uri?, val songs: List<Song>)

private fun detailData(d: Detail, state: LibraryState): DetailData = when (d) {
    is Detail.AlbumD -> {
        val songs = state.songs.filter { it.albumId == d.id }.sortedBy { it.track }
        val album = state.albums.firstOrNull { it.id == d.id }
        val artist = album?.artist ?: songs.firstOrNull()?.artist ?: ""
        DetailData(
            album?.title ?: songs.firstOrNull()?.album ?: "",
            "$artist · ${songs.size} songs",
            album?.artworkUri ?: songs.firstOrNull()?.artworkUri,
            songs,
        )
    }
    is Detail.ArtistD -> {
        val songs = state.songs.filter { it.artist == d.name }
        DetailData(d.name, "${songs.size} songs", songs.firstOrNull()?.artworkUri, songs)
    }
    is Detail.FolderD -> {
        val songs = state.songs.filter { it.path.substringBeforeLast('/') == d.path }
        val name = state.folders.firstOrNull { it.path == d.path }?.name ?: d.path.substringAfterLast('/')
        DetailData(name, "${songs.size} songs", songs.firstOrNull()?.artworkUri, songs)
    }
}

// AnimatedContent key for the detail layer (tabs use the SonaTab value itself).
private const val DETAIL_LAYER = "detail"

@Composable
fun SonaRoot(libVm: LibraryViewModel, playerVm: PlayerViewModel) {
    val state by libVm.state.collectAsStateWithLifecycle()
    val playerUi by playerVm.ui.collectAsStateWithLifecycle()

    // Nav state survives process death: tab enum + nowPlaying flag save directly, detail via its
    // id+type Saver above.
    val tab = rememberSaveable { mutableStateOf(SonaTab.Library) }
    val detail = rememberSaveable(stateSaver = DetailSaver) { mutableStateOf<Detail?>(null) }
    val nowPlaying = rememberSaveable { mutableStateOf(false) }

    // Last non-null detail, kept so SongCollectionScreen can still draw through its exit
    // animation (detail itself is nulled instantly on back). Never cleared — keep-until-next-open.
    var lastDetail by remember { mutableStateOf<Detail?>(null) }
    if (detail.value != null && detail.value != lastDetail) lastDetail = detail.value

    val curId = playerUi.current?.id
    val isPlaying = playerUi.isPlaying

    // Stable callbacks so the song-list screens stay skippable across the 500ms position ticks.
    val play = remember(playerVm) { { list: List<Song>, i: Int -> playerVm.playQueue(list, i) } }
    val shuffleAll = remember(playerVm) { { list: List<Song> -> playerVm.playShuffled(list) } }
    val toggleFav = remember(libVm) { { id: Long -> libVm.toggleFavorite(id) } }
    val openAlbum = remember { { a: Album -> detail.value = Detail.AlbumD(a.id) } }
    val openArtist = remember { { a: Artist -> detail.value = Detail.ArtistD(a.name) } }
    val openFolder = remember { { f: Folder -> detail.value = Detail.FolderD(f.path) } }

    if (nowPlaying.value) BackHandler { nowPlaying.value = false }
    else if (detail.value != null) BackHandler { detail.value = null }

    // When playback is fully cleared (notification dismissed), drop the NowPlaying flag so the
    // overlay doesn't auto-reopen the next time a song starts.
    LaunchedEffect(playerUi.hasCurrent) {
        if (!playerUi.hasCurrent) nowPlaying.value = false
    }

    // One-shot playback errors (unplayable items, skip cap reached) surface as a toast.
    val context = LocalContext.current
    LaunchedEffect(playerUi.error) {
        playerUi.error?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            playerVm.clearError()
        }
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Box(Modifier.fillMaxSize()) {
            // Layer key: detail wins over the plain tab. AnimatedContent keeps the OUTGOING layer
            // composed during its exit; lastDetail (never cleared) keeps its data alive, so the
            // detail push gets a real animated exit, not enter-only.
            val contentLayer: Any = if (detail.value != null) DETAIL_LAYER else tab.value
            AnimatedContent(
                targetState = contentLayer,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val transform = when {
                        // Detail opens: fade + slight rise over a fading tab.
                        targetState == DETAIL_LAYER ->
                            (fadeIn(AnimSpecs.normal()) + slideInVertically(AnimSpecs.normal()) { it / 20 }) togetherWith fadeOut(AnimSpecs.fast())
                        // Detail closes: it fades + sinks (kept on top via its entry zIndex).
                        initialState == DETAIL_LAYER ->
                            fadeIn(AnimSpecs.normal()) togetherWith (fadeOut(AnimSpecs.fast()) + slideOutVertically(AnimSpecs.fast()) { it / 20 })
                        // Tab <-> tab: plain 150 ms crossfade.
                        else -> fadeIn(AnimSpecs.fast()) togetherWith fadeOut(AnimSpecs.fast())
                    }
                    // An exiting layer keeps the zIndex it entered with, so a closing detail
                    // slides out OVER the tab it reveals.
                    transform.targetContentZIndex = if (targetState == DETAIL_LAYER) 1f else 0f
                    transform
                },
                label = "contentLayer",
            ) { layer ->
                if (layer == DETAIL_LAYER) {
                    val d = detail.value ?: lastDetail
                    val dd = remember(d, state.songs) { d?.let { detailData(it, state) } }
                    if (dd != null) SongCollectionScreen(
                        title = dd.title, subtitle = dd.subtitle, artworkUri = dd.art, songs = dd.songs,
                        curId = curId, isPlaying = isPlaying, favorites = state.favorites,
                        onBack = { detail.value = null },
                        onPlay = { if (dd.songs.isNotEmpty()) play(dd.songs, 0) },
                        onShuffle = { if (dd.songs.isNotEmpty()) shuffleAll(dd.songs) },
                        onPlaySong = play, onToggleFav = toggleFav,
                    )
                } else when (layer as SonaTab) {
                    SonaTab.Library -> LibraryScreen(
                        state, curId, isPlaying, play, shuffleAll, openAlbum, openArtist, openFolder, toggleFav,
                    )
                    SonaTab.Search -> SearchScreen(state, curId, isPlaying, play, toggleFav)
                    SonaTab.Settings -> SettingsScreen(state.songs.size) { libVm.load() }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            // MiniPlayer pops in when a song first exists and shrinks away when playback is
            // cleared. MiniPlayer itself remembers its last song so it stays drawn through the
            // exit — but the dying bar is still hit-testable, so onTap re-checks hasCurrent: an
            // unguarded tap would arm nowPlaying AFTER the hasCurrent cleanup effect already ran,
            // leaving a stale flag that eats back presses and pops NowPlaying open on the next play.
            AnimatedOverlay(visible = playerUi.hasCurrent, enter = AnimSpecs.miniPlayerEnter, exit = AnimSpecs.miniPlayerExit) {
                MiniPlayer(playerUi, onTap = { if (playerUi.hasCurrent) nowPlaying.value = true }, onPlayPause = { playerVm.togglePlay() }, onNext = { playerVm.next() })
            }
            BottomBar(tab.value) { tab.value = it; detail.value = null }
        }

        // NowPlaying: slide up / slide down. The screen stays composed through the exit slide;
        // it caches its last song internally, so a cleared queue still draws the exit frames.
        AnimatedOverlay(visible = nowPlaying.value && playerUi.hasCurrent, enter = AnimSpecs.nowPlayingEnter, exit = AnimSpecs.nowPlayingExit) {
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
