/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.queues

import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    /**
     * True for radio/mix queues whose initial items should be filtered against
     * the recently-played window (e.g. YouTube mixes or song radios).
     */
    val isRadioMix: Boolean get() = false

    /**
     * True for album queues. We bypass blocked artist filtering for albums so that explicitly clicked albums play fully.
     */
    val isAlbum: Boolean get() = false

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) filterItems { it.metadata?.explicit != true } else this

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) filterItems { it.metadata?.isVideoSong != true } else this

        // Keeps mediaItemIndex on the same song; if that song is removed, starts at the next kept one.
        private fun filterItems(keep: (MediaItem) -> Boolean): Status {
            val filtered = items.filter(keep)
            if (filtered.size == items.size) return this
            val start = items.getOrNull(mediaItemIndex)
            val keptBefore = items.take(mediaItemIndex.coerceAtLeast(0)).count(keep)
            return copy(
                items = filtered,
                mediaItemIndex = keptBefore.coerceAtMost((filtered.size - 1).coerceAtLeast(0)),
                position = if (start != null && keep(start)) position else 0L,
            )
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
