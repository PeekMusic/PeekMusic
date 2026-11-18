package com.metrolist.shared.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.metrolist.shared.api.artwork.YouTubeCanvasClip

@Composable
internal actual fun MobileAnimatedCoverVideo(
    url: String,
    headers: Map<String, String>,
    clip: YouTubeCanvasClip?,
    playing: Boolean,
    onError: (() -> Unit)?,
    onReadyChanged: ((Boolean) -> Unit)?,
    modifier: Modifier,
) {
    LaunchedEffect(url) { onError?.invoke() }
}
