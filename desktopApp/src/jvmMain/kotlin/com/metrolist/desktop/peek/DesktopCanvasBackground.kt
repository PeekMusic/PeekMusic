/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.desktop.peek

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.ui.components.VideoRenderer
import com.metrolist.shared.api.artwork.YouTubeCanvasClip
import com.metrolist.shared.playback.AnimatedCoverPlayer
import com.metrolist.shared.playback.VideoFrame
import com.metrolist.shared.ui.components.LocalVisualWorkActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

/**
 * PeekMusic: the Canvas (Spotify or YouTube) as the full player's background, as on Android, instead of inside the
 * cover. With [fadeEnd] it fills only the player's side next to the big lyrics and fades out toward them. Without it
 * a video narrower than the area (a portrait Spotify canvas) sits sharp in the middle and a blurred copy of the same
 * frames fills the edges. The video is decoded once and drawn twice.
 */
@Composable
fun DesktopCanvasBackground(
    url: String,
    headers: Map<String, String>,
    clips: List<YouTubeCanvasClip>,
    active: Boolean,
    fadeEnd: Boolean,
    modifier: Modifier = Modifier,
    onFrameAvailabilityChanged: (Boolean) -> Unit = {},
) {
    val visible = LocalVisualWorkActive.current
    val latestActive by rememberUpdatedState(active)
    val latestHeaders by rememberUpdatedState(headers)
    val latestClips by rememberUpdatedState(clips)
    val latestOnFrame by rememberUpdatedState(onFrameAvailabilityChanged)
    var frame by remember(url) { mutableStateOf<VideoFrame?>(null, neverEqualPolicy()) }
    var viewport by remember(url) { mutableStateOf(IntSize.Zero) }
    var letterboxed by remember(url) { mutableStateOf(false) }
    val player = remember { AnimatedCoverPlayer() }

    LaunchedEffect(player, url, visible) {
        player.play(
            url = url,
            active = combine(snapshotFlow { latestActive }, visible) { playing, shown -> playing && shown },
            headers = latestHeaders,
            clips = latestClips,
        ) {
            val hadFrame = frame != null
            frame = it
            if (hadFrame != (it != null)) latestOnFrame(it != null)
        }
    }
    LaunchedEffect(player, viewport, letterboxed) {
        if (viewport.width > 0 && viewport.height > 0) {
            delay(320)
            // The player scales frames down to the drawn size but never up, so a window-sized background would get
            // every 720p/1080p frame at full size, converted and drawn twice. Cap it; the GPU scales up the rest.
            val shrink = minOf(1f, CANVAS_BACKGROUND_MAX_FRAME_HEIGHT.toFloat() / viewport.height)
            player.setTargetSize(
                (viewport.width * shrink).toInt(),
                (viewport.height * shrink).toInt(),
                fillViewport = !letterboxed,
            )
        }
    }

    BoxWithConstraints(
        modifier =
            modifier.then(
                if (fadeEnd) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.horizontalGradient(
                                    0.6f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        val areaAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
        val current = frame
        val videoAspect = current?.let { if (it.height > 0) it.width.toFloat() / it.height else null }
        // Fill the side next to the lyrics; elsewhere a clearly narrower video keeps its shape and gets blurred edges.
        val isLetterboxed = !fadeEnd && videoAspect != null && videoAspect < areaAspect * 0.85f
        SideEffect { letterboxed = isLetterboxed }
        if (isLetterboxed) {
            VideoRenderer(
                modifier = Modifier.fillMaxSize().blur(48.dp),
                contentScale = ContentScale.Crop,
                frameProvider = { frame },
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        }
        // A narrower video fades into the blurred edges on the left and right instead of ending in a hard line.
        val shownFraction =
            if (isLetterboxed && videoAspect != null) (videoAspect / areaAspect).coerceIn(0f, 1f) else 1f
        VideoRenderer(
            modifier =
                Modifier.fillMaxSize().then(
                    if (isLetterboxed) Modifier.fadeSides(shownFraction) else Modifier,
                ),
            contentScale = if (isLetterboxed) ContentScale.Fit else ContentScale.Crop,
            frameProvider = { frame },
            onViewportSizeChanged = { width, height -> viewport = IntSize(width, height) },
        )
        // Keeps title, controls and the Peek line readable on bright videos.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.25f),
                    0.5f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Black.copy(alpha = 0.6f),
                ),
            ),
        )
    }
}

/**
 * Fades a centred video that covers [shownFraction] of the width out toward its left and right edges, over a fifth
 * of its width on each side.
 */
private fun Modifier.fadeSides(shownFraction: Float): Modifier {
    val start = (1f - shownFraction) / 2f
    val end = 1f - start
    val feather = shownFraction * 0.2f
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    start to Color.Transparent,
                    (start + feather) to Color.Black,
                    (end - feather) to Color.Black,
                    end to Color.Transparent,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

/** Frame height the background decodes into; behind the scrim and blur, more only costs CPU. */
private const val CANVAS_BACKGROUND_MAX_FRAME_HEIGHT = 540

/** PeekMusic: whether the Canvas background shows a frame, so the cover steps aside only once the video is there. */
object DesktopCanvasState {
    var backgroundShowing by mutableStateOf(false)
}
