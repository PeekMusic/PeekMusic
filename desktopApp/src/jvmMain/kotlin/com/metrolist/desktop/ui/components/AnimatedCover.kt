package com.metrolist.desktop.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import com.metrolist.shared.api.artwork.YouTubeCanvasClip
import com.metrolist.shared.playback.AnimatedCoverPlayer
import com.metrolist.shared.playback.VideoFrame
import com.metrolist.shared.ui.components.LocalVisualWorkActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

@Composable
fun AnimatedCover(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    active: Boolean = true,
    onFrameAvailabilityChanged: (Boolean) -> Unit = {},
    headers: Map<String, String> = emptyMap(), // PeekMusic: for the YouTube music video canvas
    clips: List<YouTubeCanvasClip> = emptyList(), // PeekMusic: YouTube canvas "short clips"
) {
    val latestHeaders by rememberUpdatedState(headers)
    val latestClips by rememberUpdatedState(clips)
    val visible = LocalVisualWorkActive.current
    val latestActive by rememberUpdatedState(active)
    val latestOnFrameAvailabilityChanged by rememberUpdatedState(onFrameAvailabilityChanged)
    var viewport by remember(url) { mutableStateOf(IntSize.Zero) }
    var frame by remember(url) { mutableStateOf<VideoFrame?>(null, neverEqualPolicy()) }
    val player = remember { AnimatedCoverPlayer() }

    LaunchedEffect(player, url, visible) {
        if (url == null) {
            latestOnFrameAvailabilityChanged(false)
            return@LaunchedEffect
        }
        player.play(
            url = url,
            active = combine(snapshotFlow { latestActive }, visible) { playing, shown -> playing && shown },
            headers = latestHeaders,
            clips = latestClips,
        ) {
            val hadFrame = frame != null
            frame = it
            if (hadFrame != (it != null)) latestOnFrameAvailabilityChanged(it != null)
        }
    }

    LaunchedEffect(player, viewport, contentScale) {
        if (viewport.width > 0 && viewport.height > 0) {
            delay(320)
            player.setTargetSize(viewport.width, viewport.height, fillViewport = contentScale == ContentScale.Crop)
        }
    }

    VideoRenderer(
        modifier = modifier,
        contentScale = contentScale,
        frameProvider = { frame },
        onViewportSizeChanged = { width, height ->
            viewport = IntSize(width, height)
        },
    )
}
