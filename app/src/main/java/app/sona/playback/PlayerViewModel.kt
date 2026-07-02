package app.sona.playback

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.sona.core.Song
import app.sona.core.SonaRepeat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUi(
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: SonaRepeat = SonaRepeat.OFF,
    val hasCurrent: Boolean = false,
) {
    val current: Song? get() = queue.getOrNull(index)
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private var controller: MediaController? = null
    private var queue: List<Song> = emptyList()

    private val _ui = MutableStateFlow(PlayerUi())
    val ui = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState()
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            pushState()
        }, ContextCompat.getMainExecutor(app))
        viewModelScope.launch {
            while (true) {
                val c = controller
                if (c != null && c.isPlaying) {
                    _ui.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = if (c.duration > 0) c.duration else it.durationMs,
                        )
                    }
                }
                delay(500)
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        queue = songs
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(0, songs.lastIndex.coerceAtLeast(0)), 0L)
        c.prepare()
        c.play()
        pushState()
    }

    fun playShuffled(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        queue = songs
        c.shuffleModeEnabled = true
        c.setMediaItems(songs.map { it.toMediaItem() }, songs.indices.random(), 0L)
        c.prepare()
        c.play()
        pushState()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0L)) }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun pushState() {
        val c = controller ?: return
        _ui.update {
            it.copy(
                queue = queue,
                index = c.currentMediaItemIndex,
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = if (c.duration > 0) c.duration else (queue.getOrNull(c.currentMediaItemIndex)?.durationMs ?: 0L),
                shuffle = c.shuffleModeEnabled,
                repeat = when (c.repeatMode) {
                    Player.REPEAT_MODE_ONE -> SonaRepeat.ONE
                    Player.REPEAT_MODE_ALL -> SonaRepeat.ALL
                    else -> SonaRepeat.OFF
                },
                hasCurrent = c.currentMediaItem != null,
            )
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
        super.onCleared()
    }
}

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri)
            .build()
    )
    .build()
