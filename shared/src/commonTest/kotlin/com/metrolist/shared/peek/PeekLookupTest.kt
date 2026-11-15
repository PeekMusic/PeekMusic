/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.api.lyrics.LyricsFetcher
import com.metrolist.shared.api.lyrics.LyricsTranslationClient
import com.metrolist.shared.api.lyrics.MlxBuilder
import com.metrolist.shared.api.lyrics.MlxWord
import com.metrolist.shared.model.LyricsProvider
import com.metrolist.shared.model.LyricsWithProvider
import com.metrolist.shared.repository.LyricsRepository
import com.metrolist.shared.settings.PlayerBackgroundStyle
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.SliderStyle
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeekLookupTest {
    @BeforeTest
    fun setUpSettings() {
        Settings.replaceSharedForTest(Settings(MapSettings()))
    }

    @AfterTest
    fun clearSettings() {
        Settings.replaceSharedForTest(null)
    }

    @Test
    fun defaultsAreWrittenOnlyForUnsetKeysAndOnlyOnce() {
        val settings = Settings(MapSettings("isQueueLocked" to true))
        PeekDefaults.apply(settings)
        assertTrue(settings.showLyricsOnPlayer)
        assertTrue(settings.isQueueLocked)
        assertFalse(settings.lyricsSyllableRomanizationEnabled)
        assertTrue(settings.preferWordSyncedLyrics)
        assertTrue(settings.preventDuplicateTracksInQueue)
        assertEquals("Google Translate", settings.lyricsAiProvider)
        assertTrue(settings.peekParallelLyricsLookup)
        assertEquals(PlayerBackgroundStyle.GRADIENT, settings.playerBackgroundStyle)
        assertEquals(SliderStyle.SQUIGGLY, settings.sliderStyle)
        assertTrue(settings.floatingTopBar)
        assertFalse(settings.useLegacyShuffle)
        assertEquals(PeekDefaults.VERSION, settings.peekDefaultsVersion)

        settings.showLyricsOnPlayer = false
        PeekDefaults.apply(settings)
        assertFalse(settings.showLyricsOnPlayer)
    }

    @Test
    fun hiddenSettingsArePinnedEvenWhenTheUserChangedThemBefore() {
        val settings =
            Settings(
                MapSettings(
                    "skipSilence" to true,
                    "lyricsAiProvider" to "OpenRouter",
                    "lyricsAiTranslationMode" to "Transcribed",
                ),
            )
        settings.swipeToSong = false
        PeekDefaults.pinHidden(settings)
        assertFalse(settings.skipSilence)
        assertTrue(settings.swipeToSong)
        assertEquals("Google Translate", settings.lyricsAiProvider)
        assertEquals("Automatic", settings.lyricsAiActivationMode)
        assertEquals("Literal", settings.lyricsAiTranslationMode)
        assertTrue(settings.queueYouTubeHistoryWhenOffline)
    }

    @Test
    fun parallelLookupShowsAFastFallbackWhileTheFirstProviderIsSlowButKeepsPriority() =
        runTest {
            Settings.shared.peekParallelLyricsLookup = true
            Settings.shared.lyricsProviderOrder = "BetterLyrics,Lyprio"
            Settings.shared.lyricsEnabledProviders = "BETTERLYRICS,LYPRIO"
            val preferred = LyricsWithProvider("[00:01]Preferred original lyrics", "BetterLyrics")
            val fallback =
                LyricsWithProvider(
                    MlxBuilder().line(1_000, 2_000, words = listOf(MlxWord("Fallback words", 1_000, 2_000))).build(),
                    "Lyprio/KUGOU",
                )
            val calls = mutableListOf<LyricsProvider>()
            val fetcher =
                object : LyricsFetcher {
                    override suspend fun getLyrics(
                        title: String,
                        artist: String,
                        duration: Long?,
                        album: String?,
                        videoId: String?,
                        provider: LyricsProvider,
                        isVideoContext: Boolean,
                    ): LyricsWithProvider {
                        calls += provider
                        if (provider == LyricsProvider.BETTERLYRICS) {
                            delay(4_000)
                            return preferred
                        }
                        return fallback
                    }
                }
            HttpClient(MockEngine { error("No network expected") }).use { client ->
                val repository = LyricsRepository(fetcher, LyricsTranslationClient(client))
                val updates = mutableListOf<Pair<LyricsWithProvider, Long>>()
                val result =
                    repository.getLyrics("video", "Song", "Artist", null, onLyricsAvailable = {
                        updates += it to testScheduler.currentTime
                    })
                assertEquals(preferred, result)
                assertEquals(listOf(LyricsProvider.BETTERLYRICS, LyricsProvider.LYPRIO), calls)
                assertEquals(listOf(fallback to 1_000L, preferred to 4_000L), updates)
            }
        }

    @Test
    fun parallelLookupFallsBackWithoutWaitingForEveryTimeout() =
        runTest {
            Settings.shared.peekParallelLyricsLookup = true
            Settings.shared.lyricsProviderOrder = "BetterLyrics,LrcLib"
            Settings.shared.lyricsEnabledProviders = "BETTERLYRICS,LRCLIB"
            val fallback = LyricsWithProvider("[00:01]Fallback lyrics", "LrcLib")
            val fetcher =
                object : LyricsFetcher {
                    override suspend fun getLyrics(
                        title: String,
                        artist: String,
                        duration: Long?,
                        album: String?,
                        videoId: String?,
                        provider: LyricsProvider,
                        isVideoContext: Boolean,
                    ): LyricsWithProvider? {
                        if (provider == LyricsProvider.BETTERLYRICS) {
                            delay(60_000)
                            return null
                        }
                        return fallback
                    }
                }
            HttpClient(MockEngine { error("No network expected") }).use { client ->
                val repository = LyricsRepository(fetcher, LyricsTranslationClient(client))
                var firstShownAt: Long? = null
                val result =
                    repository.getLyrics("video", "Song", "Artist", null, onLyricsAvailable = {
                        if (firstShownAt == null) firstShownAt = testScheduler.currentTime
                    })
                assertEquals(fallback, result)
                assertEquals(1_000L, firstShownAt)
            }
        }
}
