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
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.shared.api.artwork.CanvasContentSource
import com.metrolist.shared.api.artwork.YouTubeCanvasClip
import com.metrolist.shared.api.artwork.isEnabled
import com.metrolist.shared.api.artwork.youtubeCanvasPlaybackClips
import com.metrolist.shared.api.musicvideo.MusicVideoResolver
import com.metrolist.shared.api.musicvideo.MusicVideoResult
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.artworkExplicitness
import com.metrolist.shared.model.isVideoSong
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.targetHeight
import com.metrolist.shared.state.GlobalYouTubeRepository
import com.metrolist.shared.youtube.extraction.models.toContentHints
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
import org.koin.core.context.GlobalContext

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

    /** PeekMusic: request headers of a YouTube music video canvas; empty for Spotify and Apple Music. */
    var animatedCoverHeaders: Map<String, String> by mutableStateOf(emptyMap())
        private set

    /** PeekMusic: the YouTube canvas clips to cycle through ("short clips" mode); empty plays the whole video. */
    var animatedCoverClips: List<YouTubeCanvasClip> by mutableStateOf(emptyList())
        private set
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
                    Settings.shared.musicVideoPlaybackEnabled,
                    Settings.shared.youtubeCanvasMode,
                    Settings.shared.youtubeCanvasQuality,
                )
            }.distinctUntilChanged().collectLatest {
                animatedCoverFetchJob?.cancel()
                _animatedCoverUrl = null
                animatedCoverHeaders = emptyMap()
                animatedCoverClips = emptyList()
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
                // PeekMusic: a YouTube music video belongs to its own song, never kept for the next one.
                animatedCoverUrl = _animatedCoverUrl.takeIf { animatedCoverHeaders.isEmpty() },
                animatedCoversEnabled = Settings.shared.isAnimatedArtworkOrCanvasEnabled,
            )

        if (retainedCoverUrl == null) animatedCoverFetchJob?.cancel()

        currentTrack = effectiveTrack
        _animatedCoverUrl = retainedCoverUrl
        if (retainedCoverUrl == null) {
            animatedCoverHeaders = emptyMap()
            animatedCoverClips = emptyList()
        }
        animatedCoverSongId = if (retainedCoverUrl != null) effectiveTrack?.id else null

        if (effectiveTrack != null && startArtworkFetch && retainedCoverUrl == null) fetchAnimatedCover(effectiveTrack)
    }

    private fun fetchAnimatedCover(song: SongItem) {
        if (!Settings.shared.isAnimatedArtworkOrCanvasEnabled) return
        val enabledSources = desktopArtworkSources()
        if (enabledSources.isEmpty()) {
            animatedCoverFetchJob?.cancel()
            _animatedCoverUrl = null
            animatedCoverHeaders = emptyMap()
            animatedCoverClips = emptyList()
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
                                // PeekMusic: the song's YouTube music video, played muted in the cover like a canvas.
                                val youtube =
                                    async {
                                        if (CanvasContentSource.YOUTUBE_MUSIC_VIDEO !in enabledSources) return@async null
                                        desktopMusicVideoCanvas(song)
                                    }
                                Triple(spotify.await(), appleMusic.await(), youtube.await())
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                if (Settings.shared.isAnimatedArtworkOrCanvasEnabled && currentTrack?.id == songId) {
                    val chosen =
                        result?.let { (spotify, appleMusic, youtube) ->
                            CanvasContentSource
                                .parseOrder(Settings.shared.canvasContentOrder)
                                .filter { it in desktopArtworkSources() }
                                .firstNotNullOfOrNull { source ->
                                    when (source) {
                                        CanvasContentSource.SPOTIFY_CANVAS -> spotify?.let { DesktopVideoCanvas(it) }
                                        CanvasContentSource.APPLE_MUSIC -> appleMusic?.let { DesktopVideoCanvas(it) }
                                        CanvasContentSource.YOUTUBE_MUSIC_VIDEO -> youtube
                                    }
                                }
                        }
                    _animatedCoverUrl = chosen?.url
                    animatedCoverHeaders = chosen?.headers.orEmpty()
                    animatedCoverClips = chosen?.clips.orEmpty()
                    animatedCoverSongId = songId
                }
            }
    }
}

/**
 * PeekMusic: the sources picked in Settings, minus Spotify and YouTube when Canvas is off, Apple Music when animated
 * covers are off, and YouTube when music videos are off.
 */
private fun desktopArtworkSources(): Set<CanvasContentSource> {
    val settings = Settings.shared
    return CanvasContentSource
        .parseEnabled(settings.canvasEnabledSources)
        .filterTo(mutableSetOf()) {
            it.isEnabled(settings.isCanvasEnabled, settings.enableAnimatedCovers) &&
                (it != CanvasContentSource.YOUTUBE_MUSIC_VIDEO || settings.musicVideoPlaybackEnabled)
        }
}

private class DesktopVideoCanvas(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val clips: List<YouTubeCanvasClip> = emptyList(),
)

/** Video height for the YouTube canvas when the quality is Auto: it plays small inside the cover, and pixels cost CPU. */
private const val DESKTOP_VIDEO_CANVAS_AUTO_HEIGHT = 480

/**
 * PeekMusic: the song's YouTube music video as a direct video stream (no SABR, so the looping cover player can read
 * it) in the YouTube canvas quality, with the clips to play. Null when the song has no music video or YouTube offers no direct stream.
 */
private suspend fun desktopMusicVideoCanvas(song: SongItem): DesktopVideoCanvas? {
    val videoId =
        if (song.isVideoSong) {
            song.id
        } else {
            (GlobalContext.get().get<MusicVideoResolver>().resolveMusicVideo(song) as? MusicVideoResult.Found)?.videoId
        } ?: return null
    val target = if (videoId == song.id) song else GlobalYouTubeRepository.instance.getSongMetadata(videoId) ?: return null
    val resolver = GlobalYouTubeRepository.streamResolver ?: return null
    val hints =
        target
            .toContentHints(wantVideo = true)
            .copy(maxVideoHeight = Settings.shared.youtubeCanvasQuality.targetHeight ?: DESKTOP_VIDEO_CANVAS_AUTO_HEIGHT)
            .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true)
    val resource = resolver.resolve(target.id, hints, AudioQuality.AUTO, sourceSong = target)
    val url = resource.videoUrl?.takeIf { resource.hasDirectVideo && it.startsWith("http") } ?: return null
    // Short clips (Android's default) or the whole video, from the same YouTube canvas settings as Android.
    return DesktopVideoCanvas(url, resource.headers, youtubeCanvasPlaybackClips(Settings.shared.youtubeCanvasMode, resource))
}
