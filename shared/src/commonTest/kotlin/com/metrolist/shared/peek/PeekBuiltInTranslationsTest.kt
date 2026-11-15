/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.api.lyrics.LyricsUtils
import com.metrolist.shared.api.lyrics.parseTtmlToMlx
import com.metrolist.shared.settings.Settings
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeekBuiltInTranslationsTest {
    private val ttml =
        """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word" xml:lang="ko">
        <head><metadata><ttm:agent type="person" xml:id="v1"/>
        <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal"><translations><translation type="subtitle" xml:lang="en-US">
        <text for="L2">Follow me</text><text for="L1">Just sit and talk <span ttm:role="x-bg">(oh)</span></text>
        </translation></translations></iTunesMetadata>
        </metadata></head>
        <body><div>
        <p begin="1.000" end="2.000" itunes:key="L1" ttm:agent="v1"><span begin="1.000" end="2.000">그냥</span></p>
        <p begin="3.000" end="4.000" itunes:key="L2" ttm:agent="v1"><span begin="3.000" end="4.000">따라와</span></p>
        </div></body></tt>
        """.trimIndent()

    @BeforeTest
    fun setUpSettings() {
        Settings.replaceSharedForTest(Settings(MapSettings()))
    }

    @AfterTest
    fun clearSettings() {
        Settings.replaceSharedForTest(null)
    }

    @Test
    fun theMenuNamesTheProviderOfAShippedTranslation() {
        Settings.shared.lyricsAiTargetLanguage = "en"
        val mlx = parseTtmlToMlx(ttml, "Song", "Artist", "BetterLyrics")
        assertEquals(listOf("Just sit and talk (oh)", "Follow me"), LyricsUtils.parseLyrics(mlx).map { it.translatedText })
        assertEquals("BetterLyrics", PeekBuiltInTranslations.builtInSource(mlx))
    }

    @Test
    fun cachedTranslationInAnOldTargetLanguageIsHidden() {
        Settings.shared.lyricsAiTargetLanguage = "en"
        val mlx = parseTtmlToMlx(ttml, "Song", "Artist", "BetterLyrics")
        Settings.shared.lyricsAiTargetLanguage = "de"
        val lines = LyricsUtils.parseLyrics(mlx)
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.translatedText == null })
        assertNull(PeekBuiltInTranslations.builtInSource(mlx))
    }

    @Test
    fun lyricsWithoutTranslationHaveNoBuiltInSource() {
        val plain = ttml.replace(Regex("<iTunesMetadata.*</iTunesMetadata>", RegexOption.DOT_MATCHES_ALL), "")
        assertNull(PeekBuiltInTranslations.builtInSource(parseTtmlToMlx(plain, "Song", "Artist", "BetterLyrics")))
    }
}
