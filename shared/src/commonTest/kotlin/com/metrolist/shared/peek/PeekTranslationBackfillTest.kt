/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PeekTranslationBackfillTest {
    // Like Google with sl=auto: only lines in the request's dominant writing system get translated.
    private fun fakeGoogle(lines: List<String>): List<String> {
        val dominant =
            lines
                .groupingBy { PeekTranslationBackfill.scriptOf(it) }
                .eachCount()
                .maxBy { it.value }
                .key
        return lines.map { if (PeekTranslationBackfill.scriptOf(it) == dominant) "DE:$it" else it }
    }

    @Test
    fun mixedSongGetsItsMinorityLinesTranslated() =
        runTest {
            val song = listOf("Baby got me looking so crazy", "빠져버리는 daydream", "Got me feeling you", "너도 말해줄래")
            val first = fakeGoogle(song)
            assertEquals(listOf(song[1], song[3]), song.filterIndexed { i, line -> first[i] == line })
            var requests = 0
            val result =
                PeekTranslationBackfill.backfill(song, first) { group ->
                    requests++
                    fakeGoogle(group)
                }
            assertEquals(song.map { "DE:$it" }, result)
            assertEquals(1, requests, "one request for the Hangul lines")
        }

    @Test
    fun songAlreadyInTheTargetLanguageCostsNoExtraRequest() =
        runTest {
            val song = listOf("Ich bin hier", "Du bist da")
            var requests = 0
            val result =
                PeekTranslationBackfill.backfill(song, song) {
                    requests++
                    it
                }
            assertEquals(song, result)
            assertEquals(0, requests)
        }

    @Test
    fun wrongLineCountKeepsTheFirstResult() =
        runTest {
            val song = listOf("Hello there", "안녕하세요", "Good night")
            val first = listOf("Hallo", "안녕하세요", "Gute Nacht")
            assertEquals(first, PeekTranslationBackfill.backfill(song, first) { listOf("a", "b") })
            assertEquals(PeekTranslationBackfill.Script.HANGUL, PeekTranslationBackfill.scriptOf("  ...빠져"))
            assertEquals(null, PeekTranslationBackfill.scriptOf("... !!"))
        }
}
