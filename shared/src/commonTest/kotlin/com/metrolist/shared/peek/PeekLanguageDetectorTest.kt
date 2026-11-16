/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import kotlin.test.Test
import kotlin.test.assertEquals

class PeekLanguageDetectorTest {
    private fun detect(
        lines: List<String>,
        target: String,
    ) = PeekLanguageDetector.linesNeedingTranslation(lines, target)

    @Test
    fun englishSongIntoEnglishNeedsNothing() {
        val lines = listOf("I don't wanna lose you", "Baby, tell me what you feel", "", "Ooh-ooh", "Never let me go")
        assertEquals(listOf(false, false, false, false, false), detect(lines, "en"))
    }

    @Test
    fun aSingleSharedWordDoesNotMakeALineForeign() {
        val lines = listOf("I don't want a lot for Christmas", "Santa Claus won't make me happy", "All I want for Christmas is you")
        assertEquals(listOf(false, false, false), detect(lines, "en"))
    }

    @Test
    fun mixedSongTranslatesOnlyTheOtherScript() {
        val lines = listOf("You and me, ETA", "너에게로 가고 있어", "What's your ETA?")
        assertEquals(listOf(false, true, false), detect(lines, "en"))
    }

    @Test
    fun otherLatinLanguagesAreTranslated() {
        val lines = listOf("Ich liebe dich und du mich nicht", "Yo quiero estar contigo", "I know you love me")
        assertEquals(listOf(true, true, false), detect(lines, "en"))
    }

    @Test
    fun englishIsTranslatedIntoGerman() {
        assertEquals(listOf(true, false), detect(listOf("I know you love me", "Ich bin immer noch hier"), "de-DE"))
    }

    @Test
    fun linesWithoutSignalFollowTheSong() {
        val english = listOf("Tell me what you want", "Sunshine", "I know it's true")
        assertEquals(listOf(false, false, false), detect(english, "en"))
        val swedish = listOf("Jag älskar dig", "Sommarnatt", "Kom hem")
        assertEquals(listOf(true, true, true), detect(swedish, "en"))
    }

    @Test
    fun japaneseAndChineseAreToldApartByKana() {
        // Kanji-only lines can be Japanese too, so they follow the song.
        assertEquals(listOf(false, false), detect(listOf("君のことが好きだよ", "我爱你"), "ja"))
        assertEquals(listOf(true, true), detect(listOf("我爱你", "你是我的"), "ja"))
        assertEquals(listOf(true, false), detect(listOf("君のことが好きだよ", "我爱你"), "zh-CN"))
    }

    @Test
    fun unknownTargetsTranslateEverything() {
        assertEquals(listOf(true, false), detect(listOf("Hello", " "), "xx"))
    }
}
