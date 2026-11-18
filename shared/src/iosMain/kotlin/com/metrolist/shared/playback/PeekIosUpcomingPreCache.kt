/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.playback

import androidx.compose.runtime.snapshotFlow
import com.metrolist.shared.model.RepeatMode
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.peek.upcomingPreCacheTargets
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.storage.IosAudioDownloadManager
import com.metrolist.shared.utils.Logger
import com.metrolist.shared.youtube.playback.PlaybackNetworkStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * PeekMusic: caches the next few queue songs into the song cache while music plays, one at a time, like Android's
 * PeekUpcomingPreCache. Off by default; it waits a few seconds after every queue change so skipping through songs
 * downloads nothing, and it stays off mobile data unless the user allows it.
 */
internal class PeekIosUpcomingPreCache(
    private val scope: CoroutineScope,
    private val player: MusicPlayer,
    private val repeatMode: StateFlow<RepeatMode>,
    private val storage: IosAudioDownloadManager,
) {
    private companion object {
        const val TAG = "PeekPreCache"
        const val START_DELAY_MS = 8_000L
        const val MAX_ATTEMPTS = 3
        val RETRY_AFTER = 1.minutes
    }

    private data class Limits(
        val count: Int,
        val wifiOnly: Boolean,
        val cacheEnabled: Boolean,
    )

    private class Failure(
        val attempts: Int,
        val at: TimeMark,
    )

    private val failures = linkedMapOf<String, Failure>()

    fun start() {
        scope.launch {
            // Nothing until the user plays something, so a restored queue does not download on app start.
            player.isPlaying.first { it }
            val limits =
                snapshotFlow {
                    Limits(
                        Settings.shared.peekPreCacheUpcomingCount,
                        Settings.shared.peekPreCacheWifiOnly,
                        Settings.shared.songCacheEnabled && Settings.shared.maxSongCacheSize != 0,
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
        if (!canAttempt(song.id)) return
        try {
            if (storage.preCacheUpcomingSong(song)) failures.remove(song.id) else failed(song.id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failed(song.id)
            Logger.w(TAG, "Failed to cache upcoming song ${song.id}: ${error::class.simpleName}")
        }
    }

    private fun canAttempt(id: String): Boolean =
        failures[id]?.let { it.attempts < MAX_ATTEMPTS && it.at.elapsedNow() >= RETRY_AFTER } ?: true

    private fun failed(id: String) {
        failures[id] = Failure((failures[id]?.attempts ?: 0) + 1, TimeSource.Monotonic.markNow())
        while (failures.size > 64) failures.remove(failures.keys.first())
    }
}
