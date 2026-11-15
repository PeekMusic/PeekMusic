/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.android.ui.components.AnimatedCoverVideo
import com.metrolist.shared.api.artwork.AnimatedArtworkService
import com.metrolist.shared.api.artwork.DirectCanvasProvider
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.canvas
import com.metrolist.shared.generated.resources.enable_animated_covers
import com.metrolist.shared.generated.resources.peek_onboarding_preview_off
import com.metrolist.shared.generated.resources.peek_onboarding_preview_sign_in
import com.metrolist.shared.generated.resources.peek_onboarding_preview_unavailable
import com.metrolist.shared.peek.PeekSampleSong
import com.metrolist.shared.settings.Settings
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Result of looking up one moving artwork for the example song; `null` while it loads. */
private data class ArtworkLookup(
    val url: String?,
)

/**
 * The canvas page's preview with the example song: Spotify's canvas and Apple Music's animated cover, played like in
 * the player when the matching switch is on. Otherwise the plain cover with a short note why nothing moves.
 */
@Composable
internal fun PeekCanvasPreview() {
    val settings = Settings.shared
    val canvasProvider: DirectCanvasProvider = koinInject()
    val artworkService: AnimatedArtworkService = koinInject()
    val canvasOn = settings.playerCanvasEnabled
    val signedIn = settings.spotifySession.isNotBlank()
    val coversOn = settings.enableAnimatedCovers
    // Without a Spotify session the canvas still comes through a public lookup, so try it either way.
    val spotify by produceState<ArtworkLookup?>(null, canvasOn, signedIn) {
        value = null
        value =
            ArtworkLookup(
                if (canvasOn) {
                    lookup {
                        canvasProvider.getSpotifyCanvas(
                            PeekSampleSong.TITLE,
                            PeekSampleSong.ARTIST,
                            PeekSampleSong.DURATION_MS,
                            PeekSampleSong.ALBUM,
                            PeekSampleSong.ARTIST,
                        )
                    }
                } else {
                    null
                },
            )
    }
    val apple by produceState<ArtworkLookup?>(null, coversOn, settings.animatedCoverResolution) {
        value = null
        value =
            ArtworkLookup(
                if (coversOn) {
                    lookup { artworkService.fetchAnimatedCover(PeekSampleSong.TITLE, PeekSampleSong.ARTIST, PeekSampleSong.ALBUM)?.url }
                } else {
                    null
                },
            )
    }
    val off = stringResource(Res.string.peek_onboarding_preview_off)
    val unavailable = stringResource(Res.string.peek_onboarding_preview_unavailable)
    Row(
        Modifier.fillMaxWidth().height(300.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkTile(
            label = stringResource(Res.string.canvas),
            note =
                when {
                    !canvasOn -> off
                    spotify != null && spotify?.url == null && !signedIn -> stringResource(Res.string.peek_onboarding_preview_sign_in)
                    spotify != null && spotify?.url == null -> unavailable
                    else -> null
                },
            videoUrl = spotify?.url,
            modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f),
        )
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            ArtworkTile(
                label = stringResource(Res.string.enable_animated_covers),
                note =
                    when {
                        !coversOn -> off
                        apple != null && apple?.url == null -> unavailable
                        else -> null
                    },
                videoUrl = apple?.url,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
        }
    }
}

private suspend fun lookup(block: suspend () -> String?): String? =
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

@Composable
private fun ArtworkTile(
    label: String,
    note: String?,
    videoUrl: String?,
    modifier: Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(20.dp))) {
        AsyncImage(
            model = PeekSampleSong.ARTWORK_URL,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (videoUrl != null) {
            AnimatedCoverVideo(url = videoUrl, modifier = Modifier.fillMaxSize())
        }
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
