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

    @Test
    fun backgroundLinesStayWithTheMainLine() {
        val main =
            LyricsEntry(
                time = 1_000,
                text = "Main line",
                words = listOf(WordTimestamp("Main", 1.0, 1.5), WordTimestamp("line", 1.5, 3.0)),
            )
        val adlib =
            LyricsEntry(
                time = 2_000,
                text = "ooh",
                words = listOf(WordTimestamp("ooh", 2.0, 2.8)),
                isBackground = true,
            )
        val next = LyricsEntry(time = 5_000, text = "Next", endTime = 6_000)
        val merged = mergeBackgroundLines(listOf(main, adlib, next))

        assertEquals(listOf("Main line", "Next"), merged.map { it.text })
        assertEquals(0, peekLineIndex(merged, 2_400))
        val (shown, call) = splitAdlib(merged[0])
        assertEquals(listOf("Main", "line"), shown.words?.map { it.text })
        assertEquals("ooh", call?.text)
        assertEquals(2_000, call?.time)
    }

    @Test
    fun untimedBackgroundLinesBecomeBackgroundText() {
        val merged =
            mergeBackgroundLines(
                listOf(
                    LyricsEntry(time = 1_000, text = "Main line"),
                    LyricsEntry(time = 2_000, text = "ooh", isBackground = true),
                ),
            )

        assertEquals(1, merged.size)
        assertNull(merged[0].words)
        assertEquals("ooh", splitAdlib(merged[0]).second?.text)
        assertEquals("Main line", splitAdlib(merged[0]).first.text)
    }
}
