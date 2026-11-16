/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.api.lyrics.LyricsAiResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeekTranslationNotNeededTest {
    private val sources = listOf("Hello, my friend", "How are you?")

    @Test
    fun unchangedLyricsNeedNoTranslation() {
        assertTrue(
            PeekTranslationNotNeeded.isUnchanged(
                sources,
                LyricsAiResult(translatedLines = listOf("Hello my friend", "how are you")),
                "Literal",
            ),
        )
    }

    @Test
    fun translatedLyricsAreKept() {
        assertFalse(
            PeekTranslationNotNeeded.isUnchanged(
                sources,
                LyricsAiResult(translatedLines = listOf("Hallo, mein Freund", "How are you?")),
                "Literal",
            ),
        )
    }

    @Test
    fun romanizationIsNeverDropped() {
        assertFalse(PeekTranslationNotNeeded.isUnchanged(sources, LyricsAiResult(translatedLines = sources), "Romanized"))
    }
}
