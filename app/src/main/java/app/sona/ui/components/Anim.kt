package app.sona.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.sona.ui.theme.Surface2
import app.sona.ui.theme.Surface4

// ============================================================================================
// SONA motion kit — canonical specs, overlay wrapper, and skeleton/shimmer primitives.
// Deliberately tiny and allocation-light: one infinite transition per skeleton surface,
// draw-phase shimmer, no per-item animations in long LazyColumns.
// ============================================================================================

/** The app's canonical durations / easings / springs. Use these, don't invent new timings. */
object AnimSpecs {
    /** Micro interactions, exits, crossfades. */
    const val FastMs = 150
    /** Standard enters / slides. */
    const val NormalMs = 250

    val Ease: Easing = FastOutSlowInEasing

    /** 150 ms tween — exits, fades, small moves. */
    fun <T> fast(): TweenSpec<T> = tween(FastMs, easing = Ease)

    /** 250 ms tween — enters, larger moves. */
    fun <T> normal(): TweenSpec<T> = tween(NormalMs, easing = Ease)

    /** Gentle no-overshoot spring for big translations (NowPlaying slide-up). */
    val gentleOffset: SpringSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    /** Emphasized spring with a slight overshoot — the mini-player "pop". */
    val emphasizedSize: SpringSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntSize.VisibilityThreshold,
    )

    // ---- Canonical enter/exit pairs (immutable, safe to share) ----

    /** Full-screen player: slide up from the bottom / slide back down. Opaque content — no fade. */
    val nowPlayingEnter: EnterTransition = slideInVertically(gentleOffset) { it }
    val nowPlayingExit: ExitTransition = slideOutVertically(normal()) { it }

    /** Full-screen pushes (SongCollectionScreen): fade + slight rise. */
    val overlayEnter: EnterTransition = fadeIn(normal()) + slideInVertically(normal()) { it / 20 }
    val overlayExit: ExitTransition = fadeOut(fast()) + slideOutVertically(fast()) { it / 20 }

    /** Mini-player appearing above the bottom bar: expands with a pop + fade. */
    val miniPlayerEnter: EnterTransition = fadeIn(normal()) + expandVertically(emphasizedSize)
    val miniPlayerExit: ExitTransition = fadeOut(fast()) + shrinkVertically(normal())
}

// ============================================================================================
// AnimatedOverlay
// ============================================================================================

/**
 * Overlay wrapper with a real exit animation.
 *
 * CONTRACT:
 *  - [content] STAYS COMPOSED while the exit animation runs. Callers must therefore keep any
 *    data the content reads (song, detail data, ...) alive until [onFullyHidden] fires — flip
 *    only the boolean that drives [visible] on dismiss, clear data in [onFullyHidden] (or
 *    simply never clear it until the next open).
 *  - [onFullyHidden] is invoked exactly once per completed hide (after the exit animation
 *    finishes and only if the overlay was actually shown). It is NOT invoked on first
 *    composition of an already-hidden overlay.
 *  - Flipping [visible] back to true mid-exit cleanly reverses the animation.
 */
@Composable
fun AnimatedOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = AnimSpecs.overlayEnter,
    exit: ExitTransition = AnimSpecs.overlayExit,
    onFullyHidden: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    val onHidden = rememberUpdatedState(onFullyHidden)
    LaunchedEffect(state) {
        // currentState flips to false only when the exit transition has fully settled, so
        // (current || target) == false exactly once per completed hide.
        var wasShown = state.currentState
        snapshotFlow { state.currentState to state.targetState }
            .collect { (current, target) ->
                if (current || target) wasShown = true
                else if (wasShown) { wasShown = false; onHidden.value?.invoke() }
            }
    }
    AnimatedVisibility(visibleState = state, modifier = modifier, enter = enter, exit = exit) {
        content()
    }
}

// ============================================================================================
// Shimmer + skeletons
// ============================================================================================

private const val ShimmerPeriodMs = 1200

/** Sweep progress shared by every [shimmer] box under one [ShimmerHost]. Null = no host. */
private val LocalShimmerProgress = staticCompositionLocalOf<State<Float>?> { null }

/**
 * Hosts ONE infinite transition and shares its progress with every [shimmer]/skeleton inside.
 * Wrap the WHOLE skeleton layout of a screen in a single ShimmerHost — per-box cost is then
 * just a draw. Without a host, skeleton boxes render as a static dark fill (never their own
 * animation), so forgetting the host degrades gracefully instead of costing performance.
 */
@Composable
fun ShimmerHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmerHost")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ShimmerPeriodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    CompositionLocalProvider(LocalShimmerProgress provides progress) {
        Box(modifier) { content() }
    }
}

/**
 * Draws the skeleton base fill ([Surface2]) plus an animated [Surface4] gradient sweep. Reads
 * the shared progress from the nearest [ShimmerHost] purely in the draw phase — no
 * recomposition, no layout, and the gradient brush is cached per size. Combine with
 * `clip(shape)` (see [SkeletonBox]).
 */
fun Modifier.shimmer(): Modifier = composed {
    val progress = LocalShimmerProgress.current
    drawWithCache {
        val band = size.width.coerceAtLeast(60f)
        val brush = Brush.linearGradient(
            0f to Surface2, 0.5f to Surface4, 1f to Surface2,
            start = Offset.Zero,
            end = Offset(band, 0f),
        )
        onDrawBehind {
            drawRect(Surface2)
            if (progress != null) {
                // Band travels from fully off-left to fully off-right each cycle.
                val x = (size.width + band) * progress.value - band
                translate(left = x) { drawRect(brush = brush, size = Size(band, size.height)) }
            }
        }
    }
}

/** One shimmering placeholder box. Size it via [modifier]; match the real content's corners. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.clip(shape).shimmer())
}

/** Placeholder for a list row (48.dp art + title/artist bars + duration — mirrors SongRow). */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(48.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(14.dp), RoundedCornerShape(4.dp))
            Spacer(Modifier.height(6.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.45f).height(11.dp), RoundedCornerShape(4.dp))
        }
        Spacer(Modifier.width(8.dp))
        SkeletonBox(Modifier.width(28.dp).height(11.dp), RoundedCornerShape(4.dp))
    }
}
