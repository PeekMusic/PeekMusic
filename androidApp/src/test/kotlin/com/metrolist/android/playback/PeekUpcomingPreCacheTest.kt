/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.playback

import androidx.media3.common.Player
import com.metrolist.shared.model.SongItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PeekUpcomingPreCacheTest {
    private fun song(
        id: String,
        isLocal: Boolean = false,
    ) = SongItem(id, id, null, emptyList(), null, isLocal = isLocal)

    private val queue = listOf("a", "b", "c", "d", "e").map(::song)

    private fun ids(
        queue: List<SongItem>,
        index: Int,
        count: Int,
        repeat: Int = Player.REPEAT_MODE_OFF,
    ) = upcomingPreCacheTargets(queue, index, count, repeat).map(SongItem::id)

    @Test
    fun takesTheNextSongsAfterTheCurrentOne() {
        assertEquals(listOf("c", "d"), ids(queue, 1, 2))
    }

    @Test
    fun offOrRepeatOneCachesNothing() {
        assertEquals(emptyList<String>(), ids(queue, 1, 0))
        assertEquals(emptyList<String>(), ids(queue, 1, 3, Player.REPEAT_MODE_ONE))
    }

    @Test
    fun stopsAtTheEndUnlessRepeatAll() {
        assertEquals(listOf("e"), ids(queue, 3, 3))
        assertEquals(listOf("e", "a", "b"), ids(queue, 3, 3, Player.REPEAT_MODE_ALL))
    }

    @Test
    fun skipsLocalSongsAndDuplicates() {
        val mixed = listOf(song("a"), song("b", isLocal = true), song("local_x"), song("c"), song("c"), song("a"))
        assertEquals(listOf("c"), ids(mixed, 0, 5))
    }
}
