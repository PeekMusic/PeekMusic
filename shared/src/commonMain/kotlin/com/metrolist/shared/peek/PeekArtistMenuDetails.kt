/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.metrolist.shared.model.ArtistItem
import com.metrolist.shared.state.GlobalYouTubeRepository
import kotlinx.coroutines.CancellationException

/**
 * PeekMusic: the artist for the ⋮ menu, with the picture and description fetched from the artist page when the item
 * from a list lacks them, so the menu always shows the description. Android and iOS use it.
 */
@Composable
fun rememberPeekArtistMenuDetails(artist: ArtistItem): ArtistItem {
    var details by remember(artist.id) { mutableStateOf(artist) }
    LaunchedEffect(artist.id) {
        if (artist.thumbnail != null && !artist.description.isNullOrBlank()) return@LaunchedEffect
        val page =
            try {
                GlobalYouTubeRepository.instance.getArtist(artist.id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@LaunchedEffect
            }
        page
            .sections["header"]
            ?.filterIsInstance<ArtistItem>()
            ?.firstOrNull()
            ?.takeIf { it.id == artist.id }
            ?.let {
                details =
                    it.copy(
                        title = it.title.ifBlank { artist.title },
                        thumbnail = artist.thumbnail ?: it.thumbnail,
                        subscribers = it.subscribers ?: artist.subscribers,
                        description = it.description ?: artist.description,
                    )
            }
    }
    return details
}
