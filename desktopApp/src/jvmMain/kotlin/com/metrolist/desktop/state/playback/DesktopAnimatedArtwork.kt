/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.desktop.state.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.metrolist.desktop.state.app.AppState
import com.metrolist.shared.api.artwork.CanvasContentSource
import com.metrolist.shared.api.artwork.isEnabled
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.artworkExplicitness
import com.metrolist.shared.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DesktopAnimatedArtwork(
    private val scope: CoroutineScope,
) {
    private val directCanvasProvider get() = AppState.directCanvasProvider
    private var currentTrack: SongItem? = null
    private var _animatedCoverUrl: String? by mutableStateOf(null)
    var animatedCoverSongId: String? by mutableStateOf(null)
        private set
    val animatedCoverUrl: String?
        get() = resolvedAnimatedCoverUrl(currentTrack, _animatedCoverUrl, animatedCoverSongId)
    private var animatedCoverFetchJob: Job? = null

    init {
        scope.launch {
            // PeekMusic: Canvas (Spotify) and animated covers (Apple Music) are separate switches; watching only
            // "either is on" kept a running canvas after Canvas was switched off while animated covers stayed on.
            snapshotFlow {
                listOf(
                    Settings.shared.isCanvasEnabled,
                    Settings.shared.enableAnimatedCovers,
                    Settings.shared.animatedCoverResolution,
                    Settings.shared.canvasContentOrder,
                    Settings.shared.canvasEnabledSources,
                )
            }.distinctUntilChanged().collectLatest {
                animatedCoverFetchJob?.cancel()
                _animatedCoverUrl = null
                animatedCoverSongId = null
                if (Settings.shared.isAnimatedArtworkOrCanvasEnabled) currentTrack?.let(::fetchAnimatedCover)
            }
        }
    }

    fun updateTrack(
        effectiveTrack: SongItem?,
        startArtworkFetch: Boolean = true,
    ) {
        val previousTrack = currentTrack
        val retainedCoverUrl =
            retainedAnimatedCoverUrl(
                previousSongId = previousTrack?.id,
                nextSongId = effectiveTrack?.id,
                previousAlbumId = previousTrack?.album?.id,
                nextAlbumId = effectiveTrack?.album?.id,
                animatedCoverUrl = _animatedCoverUrl,
                animatedCoversEnabled = Settings.shared.isAnimatedArtworkOrCanvasEnabled,
            )

        if (retainedCoverUrl == null) animatedCoverFetchJob?.cancel()

        currentTrack = effectiveTrack
        _animatedCoverUrl = retainedCoverUrl
        animatedCoverSongId = if (retainedCoverUrl != null) effectiveTrack?.id else null

        if (effectiveTrack != null && startArtworkFetch && retainedCoverUrl == null) fetchAnimatedCover(effectiveTrack)
    }

    private fun fetchAnimatedCover(song: SongItem) {
        if (!Settings.shared.isAnimatedArtworkOrCanvasEnabled) return
        val enabledSources = desktopArtworkSources()
        if (CanvasContentSource.SPOTIFY_CANVAS !in enabledSources && CanvasContentSource.APPLE_MUSIC !in enabledSources) {
            animatedCoverFetchJob?.cancel()
            _animatedCoverUrl = null
            animatedCoverSongId = null
            return
        }
        val artist = song.artists.joinToString(", ") { it.name }
        if (artist.isBlank()) return
        val songId = song.id

        animatedCoverFetchJob?.cancel()
        animatedCoverFetchJob =
            scope.launch {
                val result =
                    try {
                        withContext(Dispatchers.IO) {
                            coroutineScope {
                                val spotify =
                                    async {
                                        if (CanvasContentSource.SPOTIFY_CANVAS !in enabledSources) return@async null
                                        directCanvasProvider.getSpotifyCanvas(
                                            title = song.title,
                                            artist = artist,
                                            album = song.album?.name,
                                            duration = song.duration?.times(1_000),
                                            primaryArtist = song.artists.firstOrNull()?.name,
                                        )
                                    }
                                val appleMusic =
                                    async {
                                        if (CanvasContentSource.APPLE_MUSIC !in enabledSources) return@async null
                                        AppState.animatedArtworkService
                                            .fetchAnimatedCover(
                                                song.title,
                                                artist,
                                                song.album?.name,
                                                song.artworkExplicitness,
                                            )?.url
                                    }
                                spotify.await() to appleMusic.await()
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                if (Settings.shared.isAnimatedArtworkOrCanvasEnabled && currentTrack?.id == songId) {
                    _animatedCoverUrl =
                        result?.let { (spotify, appleMusic) ->
                            CanvasContentSource
                                .parseOrder(Settings.shared.canvasContentOrder)
                                .filter { it in desktopArtworkSources() }
                                .firstNotNullOfOrNull { source ->
                                    when (source) {
                                        CanvasContentSource.SPOTIFY_CANVAS -> spotify
                                        CanvasContentSource.APPLE_MUSIC -> appleMusic
                                        CanvasContentSource.YOUTUBE_MUSIC_VIDEO -> null
                                    }
                                }
                        }
                    animatedCoverSongId = songId
                }
            }
    }
}

/** PeekMusic: the sources picked in Settings, minus Spotify when Canvas is off and Apple Music when animated covers are off. */
private fun desktopArtworkSources(): Set<CanvasContentSource> {
    val settings = Settings.shared
    return CanvasContentSource
        .parseEnabled(settings.canvasEnabledSources)
        .filterTo(mutableSetOf()) { it.isEnabled(settings.isCanvasEnabled, settings.enableAnimatedCovers) }
}
