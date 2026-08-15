package app.sona.playback

import android.app.Application
import android.net.Uri
import android.content.ComponentName
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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
import java.util.concurrent.Future

private const val TAG = "SonaPlayer"
private const val MAX_CONSECUTIVE_ERRORS = 3

@Immutable
data class PlayerUi(
    val queue: List<Song> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: SonaRepeat = SonaRepeat.OFF,
    val hasCurrent: Boolean = false,
    /** One-shot playback error for the UI to surface (e.g. toast); clear with [PlayerViewModel.clearError]. */
    val error: String? = null,
) {
    val current: Song? get() = queue.getOrNull(index)
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private var controller: MediaController? = null
    private var controllerFuture: Future<MediaController>? = null
    private var released = false
    private var queue: List<Song> = emptyList()
    private var pending: (() -> Unit)? = null
    private var consecutiveErrors = 0

    private val _ui = MutableStateFlow(PlayerUi())
    val ui = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) consecutiveErrors = 0
        }

        // The SKIPPING on error lives in PlaybackService (it survives the UI being
        // swiped away; doing it here too would double-skip). This side only surfaces
        // the message, using a mirrored counter driven by the same player events so
        // its idea of "the service gave up" matches the service's.
        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            val title = c.currentMediaItem?.mediaMetadata?.title ?: "unknown"
            consecutiveErrors++
            _ui.update {
                it.copy(
                    error = if (consecutiveErrors < MAX_CONSECUTIVE_ERRORS && c.hasNextMediaItem())
                        "Skipped \"$title\" — file couldn't be played"
                    else
                        "Playback stopped: couldn't play \"$title\""
                )
            }
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (released) return@addListener
            val c = try {
                future.get()
            } catch (e: Exception) {
                Log.w(TAG, "MediaController connection failed", e)
                null // leave pending intact; controls stay inert rather than crashing
            } ?: return@addListener
            controller = c.also { it.addListener(listener) }
            pushState()
            pending?.let { it(); pending = null }
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
                    delay(500)
                } else {
                    delay(1000)
                }
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        val c = controller ?: run { pending = { playQueue(songs, startIndex) }; return }
        if (songs.isEmpty()) return
        consecutiveErrors = 0 // fresh queue, fresh skip budget
        queue = songs
        c.shuffleModeEnabled = false
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex.coerceIn(0, songs.lastIndex.coerceAtLeast(0)), 0L)
        c.prepare()
        c.play()
        pushState()
    }

    fun playShuffled(songs: List<Song>) {
        val c = controller ?: run { pending = { playShuffled(songs) }; return }
        if (songs.isEmpty()) return
        consecutiveErrors = 0 // fresh queue, fresh skip budget
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

    fun clearError() { _ui.update { it.copy(error = null) } }

    private fun pushState() {
        val c = controller ?: return
        // Rebuild the display queue from the live session if we reconnected with no local queue
        // (e.g. app swiped from recents then reopened while still playing).
        if (queue.isEmpty() && c.mediaItemCount > 0) {
            queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toDisplaySong() }
        }
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
        released = true
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
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

/** Reconstruct a display-only Song from a MediaItem carried by the live session. */
private fun MediaItem.toDisplaySong(): Song {
    val m = mediaMetadata
    return Song(
        id = mediaId.toLongOrNull() ?: 0L,
        title = m.title?.toString() ?: "Unknown",
        artist = m.artist?.toString() ?: "Unknown artist",
        album = m.albumTitle?.toString() ?: "",
        albumId = 0L,
        durationMs = 0L,
        uri = localConfiguration?.uri ?: Uri.EMPTY,
        artworkUri = m.artworkUri,
        track = 0,
        path = "",
        dateAdded = 0L,
    )
}
