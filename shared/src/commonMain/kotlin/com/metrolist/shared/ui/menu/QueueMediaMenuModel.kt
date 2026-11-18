/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.menu

import androidx.compose.runtime.Composable
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.details
import com.metrolist.shared.generated.resources.details_desc
import com.metrolist.shared.generated.resources.remove_from_queue
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.peek.peekSongMenuActionGroups
import com.metrolist.shared.ui.components.PlayerMenuAction
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

@Composable
fun queueMediaMenuModel(
    songTitle: String,
    artistName: String,
    artists: List<ArtistTiny>,
    albumName: String?,
    thumbnailUrl: String?,
    isDisliked: Boolean,
    onDismiss: () -> Unit,
    onStartRadio: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onGoToAlbum: (() -> Unit)?,
    onGoToArtist: ((String) -> Unit)?,
    onShare: (() -> Unit)?,
    onAltShare: (() -> Unit)?,
    onShowInfo: (() -> Unit)?,
    onRemoveFromQueue: (() -> Unit)?,
    onDislike: (() -> Unit)?,
    deleteUploadAction: PlayerMenuAction? = null,
    onBlockArtist: (() -> Unit)? = null,
    downloadState: MediaDownloadState = MediaDownloadState.NOT_DOWNLOADED,
): MediaMenuModel =
    MediaMenuModel(
        title = songTitle,
        subtitle = artistName,
        thumbnail = thumbnailUrl,
        primaryActions =
            songPrimaryActions(
                onDismiss = onDismiss,
                onStartRadio = onStartRadio,
                onAddToPlaylist =
                    onAddToPlaylist?.let {
                        {
                            it()
                            onDismiss()
                        }
                    },
                onShare = onShare,
                onAltShare = onAltShare,
            ),
        // PeekMusic: the same order as the song and player menus (see PeekSongMenu): queue, remove, artist and album,
        // then download, dislike, block and details; no PeekParty and no refetch, as everywhere else.
        actionGroups =
            peekSongMenuActionGroups(
                queue = queueActions(onDismiss, onPlayNext, onAddToQueue),
                removal =
                    listOfNotNull(
                        onRemoveFromQueue?.let {
                            action(MetrolistIcons.Outlined.Delete, stringResource(Res.string.remove_from_queue), onDismiss, it)
                        },
                    ),
                navigation =
                    listOfNotNull(
                        artistNavigationMenuAction(artists, onGoToArtist, onDismiss),
                        albumNavigationMenuAction(onDismiss, onGoToAlbum.takeUnless { albumName.isNullOrBlank() }, albumName),
                    ),
                library =
                    listOfNotNull(
                        downloadAction(downloadState, 0, false, onDismiss, onDownload),
                        dislikeMenuAction(isDisliked, onDismiss, onDislike),
                        blockArtistMenuAction(onDismiss, onBlockArtist),
                        onShowInfo?.let {
                            PlayerMenuAction(
                                MetrolistIcons.Outlined.Info,
                                stringResource(Res.string.details),
                                stringResource(Res.string.details_desc),
                                onClick = it,
                            )
                        },
                    ),
                extras = listOfNotNull(deleteUploadAction),
            ),
    )
