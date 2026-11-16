/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.playback

import android.os.SystemClock
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.Player
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.isPodcast
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.youtube.playback.PlaybackNetworkStateStore
import com.metrolist.shared.youtube.playback.PlaybackResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/** Settings › Storage › Song cache offers these counts for caching ahead; 0 is off. */
val PeekPreCacheUpcomingCounts = listOf(0, 1, 2, 3, 5, 10)

/**
 * The next [count] songs after [currentIndex] worth caching ahead: streamed songs only, wrapping round with repeat all,
 * none with repeat one.
 */
internal fun upcomingPreCacheTargets(
    queue: List<SongItem>,
    currentIndex: Int,
    count: Int,
    repeatMode: Int,
): List<SongItem> {
    if (count <= 0 || repeatMode == Player.REPEAT_MODE_ONE || currentIndex !in queue.indices) return emptyList()
    val ahead = queue.drop(currentIndex + 1)
    val order = if (repeatMode == Player.REPEAT_MODE_ALL) ahead + queue.take(currentIndex) else ahead
    val current = queue[currentIndex].id
    return order
        .asSequence()
        .filter { !it.isLocal && !it.id.startsWith("local_") && !it.isPodcast && it.id != current }
        .distinctBy { it.id }
        .take(count)
        .toList()
}

/**
 * PeekMusic: caches the next few queue songs into the song cache while music plays, one at a time, so a patchy
 * connection does not stop the queue. Off by default; it waits a few seconds after every queue change so skipping
 * through songs downloads nothing, and it stays off mobile data unless the user allows it.
 */
internal class PeekUpcomingPreCache(
    private val scope: CoroutineScope,
    private val player: AndroidPlaybackPlayer,
    private val repeatMode: StateFlow<Int>,
    private val audioCacheManager: AudioCacheManager,
    private val fetchPlaybackResource: suspend (SongItem, Boolean) -> PlaybackResource?,
    private val activeIds: () -> Set<String>,
) {
    private companion object {
        const val TAG = "PeekPreCache"
        const val START_DELAY_MS = 8_000L
    }

    private val retryPolicy = CacheRetryPolicy(SystemClock::elapsedRealtime)

    private data class Limits(
        val count: Int,
        val wifiOnly: Boolean,
        val cacheEnabled: Boolean,
    )

    fun start() {
        scope.launch {
            // Nothing until the user plays something, so a restored queue does not download on app start.
            player.isPlaying.first { it }
            val limits =
                snapshotFlow {
                    Limits(
                        Settings.shared.peekPreCacheUpcomingCount,
                        Settings.shared.peekPreCacheWifiOnly,
                        Settings.shared.maxSongCacheSize != 0,
                    )
                }
            combine(
                player.songQueue,
                player.currentMediaItemIndex,
                repeatMode,
                PlaybackNetworkStateStore.state,
                limits,
            ) { queue, index, repeat, network, limit ->
                val allowed = limit.cacheEnabled && network.connected && !(limit.wifiOnly && network.isCellular)
                if (allowed) upcomingPreCacheTargets(queue, index, limit.count, repeat) else emptyList()
            }.distinctUntilChanged { old, new -> old.map(SongItem::id) == new.map(SongItem::id) }
                .collectLatest { targets ->
                    if (targets.isEmpty()) return@collectLatest
                    delay(START_DELAY_MS)
                    targets.forEach { cache(it) }
                }
        }
    }

    private suspend fun cache(song: SongItem) {
        // The song playing or preloading streams into the cache on its own; a second download would only compete.
        if (!retryPolicy.canAttempt(song.id) || song.id in activeIds() || audioCacheManager.isCached(song.id)) return
        try {
            val resource = fetchPlaybackResource(song, false)
            if (resource != null && audioCacheManager.preCacheUpcomingSong(song, resource)) {
                retryPolicy.succeeded(song.id)
            } else {
                retryPolicy.failed(song.id)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            retryPolicy.failed(song.id)
            Timber.tag(TAG).w(error, "Failed to cache upcoming song: %s", song.id)
        }
    }
}
