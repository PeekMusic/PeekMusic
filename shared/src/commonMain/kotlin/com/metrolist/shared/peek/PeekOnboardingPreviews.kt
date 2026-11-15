/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.shared.api.lyrics.LyricsUtils
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.peek_onboarding_high_quality
import com.metrolist.shared.generated.resources.peek_onboarding_high_quality_description
import com.metrolist.shared.generated.resources.repeat
import com.metrolist.shared.generated.resources.unlike
import com.metrolist.shared.model.AlbumTiny
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.LyricsEntry
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.repository.LyricsRepository
import com.metrolist.shared.settings.AnimatedCoverResolution
import com.metrolist.shared.settings.MusicVideoQuality
import com.metrolist.shared.settings.PlayerBackgroundStyle
import com.metrolist.shared.settings.PlayerType
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.MiniPlayer
import com.metrolist.shared.ui.components.PlayerPrimaryControls
import com.metrolist.shared.ui.components.VintageClassicControls
import com.metrolist.shared.ui.components.VintageClassicControlsText
import com.metrolist.shared.ui.components.VintagePlayerControls
import com.metrolist.shared.ui.components.vintageOnButtonColor
import com.metrolist.shared.ui.player.LyricsPlayerBackground
import com.metrolist.shared.ui.player.playerGradientStops
import com.metrolist.shared.ui.player.vintagePlayerControlsText
import com.metrolist.shared.ui.settings.LyricsSettingsPreview
import com.metrolist.shared.ui.settings.SettingsControl
import com.metrolist.shared.ui.settings.SettingsItemRegistration
import com.metrolist.shared.ui.settings.SliderStylePreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform
import kotlin.time.TimeSource

/** The example song for onboarding previews: Korean with English lines, so translation and Latin script both show. */
object PeekSampleSong {
    const val VIDEO_ID = "XCkpalH6Ppk"
    const val TITLE = "ETA"
    const val ARTIST = "NewJeans"
    const val DURATION_MS = 152_000L
    const val ALBUM = "NewJeans 2nd EP 'Get Up'"
    const val ARTWORK_URL =
        "https://yt3.googleusercontent.com/FzLKj6zFEJna0gRNDeZRH4nuQwEyN-YbCaC-bIGLoia6EhirHUachdvdEdR3VdB7pArgFCW8mtpLPL0=w544-h544-l90-rj"

    val song =
        SongItem(
            id = VIDEO_ID,
            title = TITLE,
            thumbnail = ARTWORK_URL,
            artists = listOf(ArtistTiny(id = null, name = ARTIST)),
            album = AlbumTiny(id = null, name = ALBUM),
            duration = DURATION_MS / 1000,
        )
}

/** One onboarding switch for all video and artwork qualities; the single values stay in Settings. */
object PeekQuality {
    val isHigh: Boolean
        get() =
            Settings.shared.youtubeCanvasQuality == MusicVideoQuality.P1080 &&
                Settings.shared.musicVideoQuality == MusicVideoQuality.P1080 &&
                Settings.shared.animatedCoverResolution == AnimatedCoverResolution.HIGH_1080 &&
                Settings.shared.appleMusicArtistVideoResolution == AnimatedCoverResolution.HIGH_1080

    fun setHigh(high: Boolean) {
        Settings.shared.youtubeCanvasQuality = if (high) MusicVideoQuality.P1080 else MusicVideoQuality.P480
        Settings.shared.musicVideoQuality = if (high) MusicVideoQuality.P1080 else MusicVideoQuality.AUTO
        Settings.shared.animatedCoverResolution = if (high) AnimatedCoverResolution.HIGH_1080 else AnimatedCoverResolution.MEDIUM_720
        Settings.shared.appleMusicArtistVideoResolution =
            if (high) AnimatedCoverResolution.HIGH_1080 else AnimatedCoverResolution.MEDIUM_720
    }

    const val ITEM_ID = "peekHighQuality"
}

@Composable
internal fun peekHighQualityItem(): SettingsItemRegistration =
    SettingsItemRegistration(
        id = PeekQuality.ITEM_ID,
        title = stringResource(Res.string.peek_onboarding_high_quality),
        description = stringResource(Res.string.peek_onboarding_high_quality_description),
        control = SettingsControl.SWITCH,
        checked = PeekQuality.isHigh,
        onCheckedChange = PeekQuality::setHigh,
    )

/**
 * The player with the example song: upstream's real background for the chosen style, the chosen progress bar and the
 * controls of the chosen design, then the chosen mini player. [artworkGradient] are the cover's colors from the platform.
 */
@Composable
internal fun PeekPlayerPreview(artworkGradient: List<Color>) {
    val settings = Settings.shared
    val colors = MaterialTheme.colorScheme
    val background = settings.playerBackgroundStyle
    val stops =
        remember(artworkGradient, colors, background) {
            playerGradientStops(artworkGradient).takeIf { it.isNotEmpty() }
                ?: arrayOf(0.0f to colors.primaryContainer, 0.5f to colors.tertiaryContainer, 1.0f to colors.surfaceContainer)
        }
    val onBackground = if (background == PlayerBackgroundStyle.SOLID) colors.onSurface else Color.White
    val sample = rememberPeekSampleLyrics()
    val clockFlow = rememberPeekSampleClock(sample.entries)
    val clock = clockFlow.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.clearAndSetSemantics {}) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))) {
            Box(Modifier.matchParentSize()) {
                LyricsPlayerBackground(
                    playerBackground = background,
                    thumbnailUrl = PeekSampleSong.ARTWORK_URL,
                    backgroundColor = colors.surfaceContainer,
                    gradientColorStops = stops,
                    alpha = 1f,
                )
            }
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(
                    model = PeekSampleSong.ARTWORK_URL,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(200.dp)
                            .clip(RoundedCornerShape(20.dp)),
                )
                PeekPreviewLine(lyrics = sample.entries, position = { clock.value }, textColor = onBackground)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(PeekSampleSong.TITLE, style = MaterialTheme.typography.titleLarge, color = onBackground, maxLines = 1)
                    Text(
                        PeekSampleSong.ARTIST,
                        style = MaterialTheme.typography.bodyLarge,
                        color = onBackground.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SliderStylePreview(settings.sliderStyle, Modifier.fillMaxWidth())
                PreviewControls(settings.playerType, onBackground)
            }
        }
        MiniPlayer(
            song = PeekSampleSong.song,
            isPlaying = true,
            isLoading = false,
            positionMs = { PeekSampleSong.DURATION_MS / 3 },
            durationMs = PeekSampleSong.DURATION_MS,
            onTogglePlay = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onExpand = {},
            modifier = Modifier.fillMaxWidth(),
            playbackEnabled = false,
        )
    }
}

@Composable
private fun PreviewControls(
    playerType: PlayerType,
    onBackground: Color,
) {
    val text = vintagePlayerControlsText()
    when (playerType) {
        PlayerType.MODERN -> {
            PlayerPrimaryControls(
                isPlaying = true,
                isLoading = false,
                playText = text.play,
                pauseText = text.pause,
                onSkipPrevious = {},
                onTogglePlay = {},
                onSkipNext = {},
                buttonHeight = 64.dp,
                showLabels = false,
                useRoundedRect = Settings.shared.useRoundedRectPlayButton,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PlayerType.VINTAGE -> {
            VintagePlayerControls(
                text = text,
                isPlaying = true,
                isLoading = false,
                onSkipPrevious = {},
                onTogglePlay = {},
                onSkipNext = {},
                buttonSize = 64.dp,
                iconSize = 44.dp,
                sideButtonColor = onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PlayerType.CLASSIC -> {
            VintageClassicControls(
                text =
                    VintageClassicControlsText(
                        repeat = stringResource(Res.string.repeat),
                        previous = text.previous,
                        play = text.play,
                        pause = text.pause,
                        next = text.next,
                        like = stringResource(Res.string.action_like),
                        unlike = stringResource(Res.string.unlike),
                    ),
                isPlaying = true,
                isLoading = false,
                repeatMode = 0,
                isLiked = true,
                onToggleRepeat = {},
                onSkipPrevious = {},
                onTogglePlay = {},
                onSkipNext = {},
                onToggleLike = {},
                contentColor = onBackground,
                buttonColor = onBackground,
                onButtonColor = vintageOnButtonColor(onBackground),
                buttonSize = 64.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The example song's lyrics, enriched like in the player (translation and Latin script follow the settings). */
internal class PeekSampleLyrics(
    val entries: List<LyricsEntry>,
    val provider: String?,
)

@Composable
internal fun rememberPeekSampleLyrics(): PeekSampleLyrics {
    val repository = remember { KoinPlatform.getKoin().get<LyricsRepository>() }
    var raw by remember { mutableStateOf<Pair<String, String>?>(null) }
    var entries by remember { mutableStateOf<List<LyricsEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        raw =
            try {
                repository
                    .getLyrics(
                        videoId = PeekSampleSong.VIDEO_ID,
                        title = PeekSampleSong.TITLE,
                        artist = PeekSampleSong.ARTIST,
                        duration = PeekSampleSong.DURATION_MS,
                    )?.let { it.lyrics to it.provider }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
    }
    val settings = Settings.shared
    LaunchedEffect(
        raw,
        settings.lyricsAiTranslationEnabled,
        settings.lyricsAiTargetLanguage,
        settings.lyricsRomanizationEnabled,
    ) {
        val lyrics = raw?.first ?: return@LaunchedEffect
        val parsed = LyricsUtils.parseMlxDocument(lyrics)?.lines ?: LyricsUtils.parseLyrics(lyrics)
        entries = parsed
        entries =
            try {
                repository.enrichLyricsEntries(PeekSampleSong.VIDEO_ID, parsed)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                parsed
            }
    }
    return PeekSampleLyrics(entries, raw?.second)
}

/** A looping playback clock for previews, from the first sung line to the end of the example song. */
@Composable
internal fun rememberPeekSampleClock(entries: List<LyricsEntry>): MutableStateFlow<Long> {
    val position = remember { MutableStateFlow(0L) }
    LaunchedEffect(entries.isNotEmpty()) {
        if (entries.isEmpty()) return@LaunchedEffect
        val start = entries.firstOrNull { it.text.isNotBlank() }?.time ?: 0L
        val started = TimeSource.Monotonic.markNow()
        while (true) {
            position.value = start + started.elapsedNow().inWholeMilliseconds % (PeekSampleSong.DURATION_MS - start)
            delay(50)
        }
    }
    return position
}

/**
 * Upstream's lyrics preview with the example song instead of its default: the lyrics go through the same translation
 * and romanization step as in the player, so switching those settings shows right here.
 */
@Composable
internal fun PeekSampleLyricsPreview(modifier: Modifier) {
    val sample = rememberPeekSampleLyrics()
    val position = rememberPeekSampleClock(sample.entries)
    LyricsSettingsPreview(
        modifier = modifier,
        songId = PeekSampleSong.VIDEO_ID,
        currentLyrics = sample.entries,
        currentProvider = sample.provider,
        currentPosition = position,
        positionReader = { position.value },
        isPlaying = true,
    )
}
