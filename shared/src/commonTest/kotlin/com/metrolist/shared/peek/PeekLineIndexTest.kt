/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.WordTimestamp
import com.metrolist.shared.viewmodels.PlayerViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeekLineIndexTest {
    private val lyrics =
        listOf(
            LyricsEntry(time = 2_000, text = "First", endTime = 4_000),
            LyricsEntry(time = 10_000, text = "Second", endTime = 12_000),
        )

    @Test
    fun showsTheFirstLineBeforeLyricsStart() {
        assertEquals(0, peekLineIndex(lyrics, 0))
    }

    @Test
    fun showsTheCurrentLineWhileItIsSung() {
        assertEquals(0, peekLineIndex(lyrics, 3_000))
        assertEquals(1, peekLineIndex(lyrics, 11_000))
    }

    @Test
    fun showsTheUpcomingLineOnceTheCurrentOneHasEnded() {
        assertEquals(1, peekLineIndex(lyrics, 5_000))
        assertEquals(1, peekLineIndex(lyrics, 9_500))
    }

    @Test
    fun keepsTheLastLineAfterItEnds() {
        assertEquals(1, peekLineIndex(lyrics, 20_000))
    }

    @Test
    fun splitsBackgroundVocalsOutOfTheLine() {
        val entry =
            LyricsEntry(
                time = 1_000,
                text = "Hello (ooh)",
                words =
                    listOf(
                        WordTimestamp("Hello", 1.0, 1.5),
                        WordTimestamp("ooh", 1.6, 2.0, hasTrailingSpace = false, isBackground = true),
                    ),
            )
        val (main, adlib) = splitAdlib(entry)
        assertEquals(listOf("Hello"), main.words?.map { it.text })
        assertEquals("ooh", adlib?.text)
        assertEquals(1_600L, adlib?.time)
        assertEquals(2_000L, adlib?.endTime)
        assertEquals(false, adlib?.words?.single()?.isBackground)
    }

    @Test
    fun linesWithoutBackgroundVocalsHaveNoAdlib() {
        assertNull(splitAdlib(lyrics.first()).second)
    }

    @Test
    fun peekHidesDuringVideoUntilLyricsAreRetimed() {
        assertTrue(peekLyricsMatchPlayback(PlayerViewModel.PlayerUiState()))
        assertFalse(peekLyricsMatchPlayback(PlayerViewModel.PlayerUiState(isVideoModeActive = true)))
        assertTrue(peekLyricsMatchPlayback(PlayerViewModel.PlayerUiState(isVideoModeActive = true, mvLyricsRetimed = true)))
    }
}
