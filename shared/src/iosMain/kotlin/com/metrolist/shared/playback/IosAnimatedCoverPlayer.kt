package com.metrolist.shared.playback

import com.metrolist.shared.api.artwork.CanvasPlaybackWatchdog
import com.metrolist.shared.api.artwork.YouTubeCanvasClip
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayerActionAtItemEndNone
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerLooper
import platform.AVFoundation.AVPlayerLooperStatusFailed
import platform.AVFoundation.AVQueuePlayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.actionAtItemEnd
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVFoundation.status
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
internal class IosAnimatedCoverPlayer(
    url: String,
    headers: Map<String, String>,
    clips: List<YouTubeCanvasClip> = emptyList(),
) {
    internal val player = AVQueuePlayer()
    val layer = AVPlayerLayer.playerLayerWithPlayer(player)
    private val looper: AVPlayerLooper?
    private val item: AVPlayerItem?

    // PeekMusic: a YouTube music video Canvas plays its short clips one after another and starts over, like Android.
    private val clips = clips.filter { it.endMs > it.startMs }
    private var clipIndex = 0
    private var seeking = false
    private val watchdog = CanvasPlaybackWatchdog()
    private var expectingFrames = false
    private var closed = false
    private var lastPosition: Double? = null
    private var frameProgress = 0L

    init {
        player.muted = true
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        item =
            NSURL.URLWithString(url)?.let { nativeUrl ->
                AVPlayerItem(
                    asset =
                        AVURLAsset(
                            uRL = nativeUrl,
                            options = headers.takeIf { it.isNotEmpty() }?.let { mapOf("AVURLAssetHTTPHeaderFieldsKey" to it) },
                        ),
                )
            }
        looper =
            if (this.clips.isEmpty()) {
                item?.let { AVPlayerLooper.playerLooperWithPlayer(player, it) }
            } else {
                item?.let {
                    player.actionAtItemEnd = AVPlayerActionAtItemEndNone
                    player.insertItem(it, afterItem = null)
                    seekToClip(0)
                }
                null
            }
    }

    private fun seekToClip(index: Int) {
        clipIndex = index
        seeking = true
        // Exact seeks: a keyframe seek could land seconds outside a three-second clip.
        player.seekToTime(
            time = CMTimeMake(value = clips[index].startMs, timescale = 1000),
            toleranceBefore = CMTimeMake(value = 0, timescale = 1),
            toleranceAfter = CMTimeMake(value = 0, timescale = 1),
        ) { _ -> seeking = false }
    }

    /** Moves on to the next clip once the current one has played; MobileAnimatedCoverVideo calls this every 100 ms. */
    fun advanceClip() {
        if (closed || clips.isEmpty() || seeking) return
        val positionMs = CMTimeGetSeconds(player.currentTime()).takeIf { it.isFinite() }?.times(1000) ?: return
        val clip = clips[clipIndex]
        if (positionMs >= clip.endMs || positionMs < clip.startMs - CLIP_SEEK_SLACK_MS) {
            seekToClip((clipIndex + 1) % clips.size)
        }
    }

    fun setPlaying(playing: Boolean) {
        if (closed || expectingFrames == playing) return
        expectingFrames = playing
        watchdog.reset()
        if (playing) player.play() else player.pause()
    }

    val ready: Boolean get() = !closed && layer.readyForDisplay

    fun hasFailed(nowMs: Long): Boolean {
        if (closed) return false
        if (item == null || looper?.status == AVPlayerLooperStatusFailed ||
            player.currentItem?.status == AVPlayerItemStatusFailed
        ) {
            return true
        }
        val position = CMTimeGetSeconds(player.currentTime())
        // AVPlayerLayer supplies no frame callback; count playback progress only after its first displayed frame.
        if (ready && position.isFinite() && position != lastPosition) {
            frameProgress++
            lastPosition = position
        }
        return watchdog.isStalled(nowMs, frameProgress, expectingFrames)
    }

    fun close() {
        if (closed) return
        closed = true
        expectingFrames = false
        player.pause()
        looper?.disableLooping()
        layer.player = null
        player.removeAllItems()
    }
}

// A position this far before the clip means the item started over on its own, so seek back into the clip.
private const val CLIP_SEEK_SLACK_MS = 1_000.0
