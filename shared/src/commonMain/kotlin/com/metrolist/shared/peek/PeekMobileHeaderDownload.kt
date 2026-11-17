/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.peek

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.cancel
import com.metrolist.shared.generated.resources.remove
import com.metrolist.shared.generated.resources.stored_remove_selected_message
import com.metrolist.shared.generated.resources.stored_remove_selected_title
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.ui.components.AlertDialog
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The album/playlist header download button for the shared mobile UI (iOS), built from the downloaded song ids alone.
 * Without a download queue to watch there is no progress ring; a tap downloads the missing songs, or asks before
 * removing the downloads once every song is stored. Android builds its own with progress (PeekCollectionDownloadProgress).
 */
@Composable
internal fun rememberPeekMobileHeaderDownload(
    songs: List<SongItem>,
    downloadedSongIds: Set<String>,
    onDownload: (List<SongItem>) -> Unit,
    onRemove: (List<String>) -> Unit,
): PeekHeaderDownload? {
    val storable = songs.filterNot { it.isLocal }
    var pendingRemoval by remember { mutableStateOf<List<String>?>(null) }
    pendingRemoval?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(pluralStringResource(Res.plurals.stored_remove_selected_title, ids.size, ids.size)) },
            text = { Text(stringResource(Res.string.stored_remove_selected_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        onRemove(ids)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
    if (storable.isEmpty()) return null
    val isDownloaded = storable.all { it.id in downloadedSongIds }
    return PeekHeaderDownload(
        isDownloaded = isDownloaded,
        onClick = {
            if (isDownloaded) {
                pendingRemoval = storable.map { it.id }
            } else {
                onDownload(storable.filter { it.id !in downloadedSongIds })
            }
        },
    )
}
