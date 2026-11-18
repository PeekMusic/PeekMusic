/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.model.RepeatMode
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.isPodcast

/** Settings › Storage › Song cache offers these counts for caching ahead; 0 is off. */
val PeekPreCacheUpcomingCounts = listOf(0, 1, 2, 3, 5, 10)

/**
 * PeekMusic: the next [count] songs after [currentIndex] worth caching ahead: streamed songs only, wrapping round with
 * repeat all, none with repeat one. Android and iOS cache these while music plays.
 */
fun upcomingPreCacheTargets(
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
