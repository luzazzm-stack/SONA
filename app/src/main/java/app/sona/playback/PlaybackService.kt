package app.sona.playback

import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

private const val TAG = "SonaPlayback"
private const val MAX_CONSECUTIVE_ERRORS = 3

/** Foreground media playback service backed by ExoPlayer + a MediaSession. */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    // Skip-on-error lives HERE (not the ViewModel): the service is the only component
    // guaranteed alive while music plays with the app swiped away, and a single corrupt
    // or moved file must not halt an unattended queue. The ViewModel only surfaces the
    // error text; both keep parallel counters off the same player events, so their idea
    // of "gave up" stays in sync without any cross-process bookkeeping.
    private var consecutiveErrors = 0
    private val errorSkipListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) consecutiveErrors = 0
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // A fresh queue gets a fresh skip budget.
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) consecutiveErrors = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            val player = session?.player ?: return
            val title = player.currentMediaItem?.mediaMetadata?.title ?: "unknown"
            Log.w(TAG, "Unplayable item \"$title\"", error)
            consecutiveErrors++
            if (consecutiveErrors < MAX_CONSECUTIVE_ERRORS && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(errorSkipListener)
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
