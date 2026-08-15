package app.sona.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sona.core.SonaRepeat
import app.sona.core.asDuration
import app.sona.playback.PlayerUi
import app.sona.ui.components.AnimSpecs
import app.sona.ui.theme.Accent
import app.sona.ui.theme.BgBase
import app.sona.ui.theme.BorderSubtle
import app.sona.ui.theme.IconDefault
import app.sona.ui.theme.OnAccent
import app.sona.ui.theme.ScrimStrong
import app.sona.ui.theme.Surface2
import app.sona.ui.theme.Surface3
import app.sona.ui.theme.TextMuted
import app.sona.ui.theme.TextPrimary
import app.sona.ui.theme.TextSecondary
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** No-overshoot settle for freshly-changed artwork (0.97f -> 1f). */
private val ArtSettleSpring: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Slight-overshoot pop shared by the favorite heart and the play/pause button. */
private val PopSpring: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)

@Composable
fun NowPlayingScreen(
    ui: PlayerUi,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFav: (Long) -> Unit,
) {
    // Keep the last non-null song so the screen stays rendered through its exit animation after
    // playback is cleared (mirrors the MiniPlayer pattern). Only the overlay's short exit window
    // ever reads the cached value.
    val current = ui.current
    var lastSong by remember { mutableStateOf(current) }
    SideEffect { if (current != null && current != lastSong) lastSong = current }
    val song = current ?: lastSong
    if (song == null) {
        // The degenerate exit-window box must still shield what's behind it — the dying overlay
        // remains hit-testable through its slide-out, same as the full screen below.
        Box(Modifier.fillMaxSize().background(BgBase).pointerInput(Unit) { detectTapGestures { } })
        return
    }

    // ---- Motion state: one settle Animatable + a handful of single-spring states. ----
    val context = LocalContext.current
    val artRequest = remember(song.artworkUri) {
        ImageRequest.Builder(context)
            .data(song.artworkUri)
            .crossfade(AnimSpecs.NormalMs)
            .build()
    }
    val artScale = remember { Animatable(1f) }
    LaunchedEffect(song.id) {
        artScale.snapTo(0.97f)
        artScale.animateTo(1f, ArtSettleSpring)
    }
    val likeScale = remember { Animatable(1f) }
    var likePopArmed by remember { mutableStateOf(false) }
    // The pop is feedback for a TAP only: a skip can also flip `isFavorite` (favorite ->
    // non-favorite track), which must settle silently — likePopId tracks the song so a track
    // change never pops.
    var likePopId by remember { mutableLongStateOf(song.id) }
    LaunchedEffect(isFavorite, song.id) {
        if (song.id != likePopId) {
            likePopId = song.id // `isFavorite` changed because the TRACK changed — no pop
        } else if (likePopArmed) {
            likeScale.snapTo(if (isFavorite) 0.6f else 0.85f)
            likeScale.animateTo(1f, PopSpring)
        } // else: no pop for the state the screen opened with
        likePopArmed = true
    }
    val playScale by animateFloatAsState(
        targetValue = if (ui.isPlaying) 1f else 0.94f,
        animationSpec = PopSpring,
        label = "playScale",
    )

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var scrubDur by remember { mutableLongStateOf(0L) }
    var scrubId by remember { mutableLongStateOf(0L) }
    val progress = when {
        scrubbing -> scrubValue
        ui.durationMs > 0 -> (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownPos = if (scrubbing) (scrubValue * scrubDur).toLong() else ui.positionMs

    // Root tap consumer: dead areas (chrome, spacers, padding) must not let taps fall through to
    // the list stacked behind this overlay. Children are hit-tested first, so every control
    // below keeps working — the root only swallows taps nobody else claimed.
    Box(Modifier.fillMaxSize().background(BgBase).pointerInput(Unit) { detectTapGestures { } }) {
        AsyncImage(
            model = song.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(55.dp),
        )
        Box(Modifier.matchParentSize().background(ScrimStrong))

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { onClose() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "close", tint = TextPrimary, modifier = Modifier.size(30.dp))
                }
                Text("NOW PLAYING", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.size(44.dp))
            }

            Spacer(Modifier.weight(1f))
            // Inline AlbumArt-alike so the artwork can crossfade (250 ms) and settle in scale on
            // track change. graphicsLayer reads artScale in the layer block only, so the spring
            // never recomposes this subtree.
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .graphicsLayer { scaleX = artScale.value; scaleY = artScale.value }
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Surface3, Surface2))),
                contentAlignment = Alignment.Center,
            ) {
                Text(song.title.trim().firstOrNull()?.uppercase() ?: "♪", color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                AsyncImage(
                    model = artRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(48.dp).clickable { onToggleFav(song.id) }, contentAlignment = Alignment.Center) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "favorite", tint = if (isFavorite) Accent else IconDefault,
                        modifier = Modifier.size(26.dp).graphicsLayer { scaleX = likeScale.value; scaleY = likeScale.value },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = progress,
                onValueChange = { if (!scrubbing) { scrubbing = true; scrubDur = ui.durationMs; scrubId = song.id }; scrubValue = it },
                onValueChangeFinished = {
                    // Recompute against the CURRENT item: Media3 may have auto-advanced mid-drag,
                    // and a seek computed from the old track's duration would land at a nonsense
                    // position in the new one — drop the seek if the media item changed.
                    if (ui.current?.id == scrubId && ui.durationMs > 0) onSeek((scrubValue * ui.durationMs).toLong())
                    scrubbing = false
                },
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = BorderSubtle),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(shownPos.asDuration(), color = TextMuted, fontSize = 11.sp)
                Text(ui.durationMs.asDuration(), color = TextMuted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clickable { onToggleShuffle() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Shuffle, "shuffle", tint = if (ui.shuffle) Accent else IconDefault, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.size(48.dp).clickable { onPrev() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SkipPrevious, "previous", tint = TextPrimary, modifier = Modifier.size(34.dp))
                }
                Box(Modifier.size(72.dp).graphicsLayer { scaleX = playScale; scaleY = playScale }.clip(CircleShape).background(Accent).clickable { onPlayPause() }, contentAlignment = Alignment.Center) {
                    Icon(if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "play/pause", tint = OnAccent, modifier = Modifier.size(36.dp))
                }
                Box(Modifier.size(48.dp).clickable { onNext() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SkipNext, "next", tint = TextPrimary, modifier = Modifier.size(34.dp))
                }
                Box(Modifier.size(48.dp).clickable { onCycleRepeat() }, contentAlignment = Alignment.Center) {
                    Icon(
                        if (ui.repeat == SonaRepeat.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "repeat", tint = if (ui.repeat != SonaRepeat.OFF) Accent else IconDefault, modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
