/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.desktop.peek

import androidx.compose.runtime.snapshotFlow
import com.metrolist.desktop.state.app.AppState
import com.metrolist.desktop.state.playback.PlayerState
import com.metrolist.desktop.storage.DownloadManager
import com.metrolist.desktop.storage.SongCacheManager
import com.metrolist.shared.model.RepeatMode
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.isPodcast
import com.metrolist.shared.settings.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Settings › Storage › Song cache offers these counts for caching ahead; 0 is off. Same as Android. */
val PeekPreCacheUpcomingCounts = listOf(0, 1, 2, 3, 5, 10)

/**
 * The next [count] songs after [currentIndex] worth caching ahead: streamed songs only, wrapping round with repeat all,
 * none with repeat one. The desktop twin of Android's upcomingPreCacheTargets.
 */
internal fun desktopPreCacheTargets(
    queue: List<SongItem>,
    currentIndex: Int,
    count: Int,
    repeatMode: RepeatMode,
): List<SongItem> {
    if (count <= 0 || repeatMode == RepeatMode.ONE || currentIndex !in queue.indices) return emptyList()
    val ahead = queue.drop(currentIndex + 1)
    val order = if (repeatMode == RepeatMode.ALL) ahead + queue.take(currentIndex) else ahead
    val current = queue[currentIndex].id
    return order
        .asSequence()
        .filter { !it.isLocal && !it.id.startsWith("local_") && !it.isPodcast && !it.isUnavailable && it.id != current }
        .distinctBy { it.id }
        .take(count)
        .toList()
}

/**
 * PeekMusic: caches the next few queue songs into the song cache while music plays, like Android's
 * PeekUpcomingPreCache. Off by default and only with the song cache on; it waits a few seconds after every queue
 * change so skipping through songs downloads nothing. SongCacheManager downloads one song at a time.
 */
object DesktopUpcomingPreCache {
    private const val START_DELAY_MS = 8_000L
    private var started = false

    private data class Limits(
        val count: Int,
        val cacheEnabled: Boolean,
    )

    fun start() {
        if (started) return
        started = true
        AppState.scope.launch {
            // Nothing until the user plays something, so a restored queue does not download on app start.
            snapshotFlow { PlayerState.isPlaying }.first { it }
            val limits =
                snapshotFlow {
                    Limits(
                        Settings.shared.peekPreCacheUpcomingCount,
                        Settings.shared.songCacheEnabled && SongCacheManager.getMaxCacheSizeMB() != 0L,
                    )
                }
            combine(
                PlayerState.queue.state,
                snapshotFlow { PlayerState.repeatMode },
                limits,
            ) { queue, repeat, limit ->
                if (!limit.cacheEnabled) {
                    emptyList()
                } else {
                    desktopPreCacheTargets(queue.entries.map { it.song }, queue.currentIndex, limit.count, repeat)
                }
            }.distinctUntilChanged { old, new -> old.map(SongItem::id) == new.map(SongItem::id) }
                .collectLatest { targets ->
                    if (targets.isEmpty()) return@collectLatest
                    delay(START_DELAY_MS)
                    targets
                        .filterNot { SongCacheManager.isSongCached(it.id) || DownloadManager.isDownloaded(it.id) }
                        .forEach { SongCacheManager.cacheSong(it) }
                }
        }
    }
}
