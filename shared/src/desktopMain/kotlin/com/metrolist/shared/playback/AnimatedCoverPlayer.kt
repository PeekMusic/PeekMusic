package com.metrolist.shared.playback

import com.metrolist.shared.api.artwork.YouTubeCanvasClip
import com.metrolist.shared.youtube.playback.PlaybackEventKind
import com.metrolist.shared.youtube.playback.PlaybackEventLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AnimatedCoverPlayer {
    private val session = Mutex()
    private val sizeLock = Any()
    private var targetWidth = 0
    private var targetHeight = 0
    private var fillViewport = false
    private var player: VideoPlayer? = null

    suspend fun play(
        url: String,
        active: Flow<Boolean>,
        headers: Map<String, String> = emptyMap(), // PeekMusic: a YouTube music video canvas needs its request headers
        clips: List<YouTubeCanvasClip> = emptyList(), // PeekMusic: YouTube canvas "short clips", as on Android
        onFrameChanged: (VideoFrame?) -> Unit,
    ) {
        session.withLock {
            currentCoroutineContext().ensureActive()
            val ended = CompletableDeferred<Unit>()
            try {
                val player =
                    synchronized(sizeLock) {
                        VideoPlayer(
                            onFrame = { onFrameChanged(it) },
                            onEnded = {
                                onFrameChanged(null)
                                ended.complete(Unit)
                            },
                            looping = true,
                        ).also {
                            this.player = it
                            it.setTargetSize(targetWidth, targetHeight, fillViewport)
                            // PeekMusic: a YouTube music video (it needs headers) is bigger than a canvas clip.
                            it.peekAllDecoderThreads = headers.isNotEmpty() || clips.isNotEmpty()
                        }
                    }
                val loaded =
                    coroutineScope {
                        // PeekMusic: a YouTube canvas starts right at its first clip, not at 0:00.
                        val loading = async(Dispatchers.IO) { player.load(url, headers, positionProvider = { clips.firstOrNull()?.startMs ?: 0L }) }
                        try {
                            loading.await()
                        } catch (error: CancellationException) {
                            player.dispose()
                            throw error
                        }
                    }
                if (!loaded) return
                player.setVolume(0f)
                coroutineScope {
                    val activity =
                        launch {
                            active.distinctUntilChanged().collect { playing ->
                                if (playing) player.resume() else player.pause()
                            }
                        }
                    val clipCycle = if (clips.isEmpty()) null else launch { cycleClips(player, clips, active) }
                    try {
                        ended.await()
                    } finally {
                        clipCycle?.cancel()
                        activity.cancel()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                PlaybackEventLog.error(PlaybackEventKind.PLAYER, "AnimatedCover", "Animated cover failed", error)
            } catch (error: LinkageError) {
                PlaybackEventLog.error(PlaybackEventKind.PLAYER, "AnimatedCover", "Animated cover native library failed", error)
            } finally {
                // A replacement must wait for native preparation, decoding and drawing to finish.
                val retiring = synchronized(sizeLock) { player.also { player = null } }
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { retiring?.disposeAsync()?.join() }
                    onFrameChanged(null)
                }
            }
        }
    }

    /**
     * PeekMusic: plays the clips in turn like Android's runYouTubeCanvasPlaybackClock: each one for its length of
     * playing time, then on to the next, round and round. Paused time does not count.
     */
    private suspend fun cycleClips(
        player: VideoPlayer,
        clips: List<YouTubeCanvasClip>,
        active: Flow<Boolean>,
    ) {
        var index = 0
        var started = false
        while (true) {
            val clip = clips[index]
            // The player already starts at the first clip; seeking there again would show its start twice.
            if (started) player.seekTo(clip.startMs)
            started = true
            var playedMs = 0L
            val lengthMs = (clip.endMs - clip.startMs).coerceAtLeast(CLIP_TICK_MS)
            while (playedMs < lengthMs) {
                active.first { it }
                delay(CLIP_TICK_MS)
                playedMs += CLIP_TICK_MS
            }
            index = (index + 1) % clips.size
        }
    }

    private companion object {
        const val CLIP_TICK_MS = 100L
    }

    fun setTargetSize(
        width: Int,
        height: Int,
        fillViewport: Boolean,
    ) {
        synchronized(sizeLock) {
            targetWidth = width
            targetHeight = height
            this.fillViewport = fillViewport
            player?.setTargetSize(width, height, fillViewport)
        }
    }
}
