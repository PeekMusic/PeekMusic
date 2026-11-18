package com.metrolist.shared.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.metrolist.shared.api.artwork.YouTubeCanvasClip
import com.metrolist.shared.youtube.playback.PlaybackResource

/** Plays [url] muted on a loop; with [clip], only that part of the video. */
@Composable
internal expect fun MobileAnimatedCoverVideo(
    url: String,
    headers: Map<String, String>,
    clip: YouTubeCanvasClip?,
    playing: Boolean,
    onError: (() -> Unit)?,
    onReadyChanged: ((Boolean) -> Unit)?,
    modifier: Modifier,
)

/**
 * PeekMusic: whether AVPlayer can play this music video Canvas straight from its URL. iOS has no SABR, WebM or VP9
 * playback, and most iPhones cannot decode AV1, so those streams fall through to the next Canvas source.
 */
internal fun PlaybackResource.isDirectMobileCanvasVideo(): Boolean {
    if (!hasDirectVideo) return false
    val mime = videoMimeType.orEmpty().lowercase()
    val codecs = videoCodecs.orEmpty().lowercase()
    return "webm" !in mime && listOf("vp9", "vp09", "av01").none { it in codecs }
}
