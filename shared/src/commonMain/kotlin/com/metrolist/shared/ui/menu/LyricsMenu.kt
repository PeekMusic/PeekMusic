@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.metrolist.shared.api.lyrics.LyricsUtils
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.ai_translation_for_track
import com.metrolist.shared.generated.resources.cancel
import com.metrolist.shared.generated.resources.copied
import com.metrolist.shared.generated.resources.copy
import com.metrolist.shared.generated.resources.edit
import com.metrolist.shared.generated.resources.edit_lyrics
import com.metrolist.shared.generated.resources.lyrics_offset
import com.metrolist.shared.generated.resources.lyrics_romanization_show_original
import com.metrolist.shared.generated.resources.lyrics_romanize_title
import com.metrolist.shared.generated.resources.lyrics_show_translations
import com.metrolist.shared.generated.resources.no_provider_results_found
import com.metrolist.shared.generated.resources.offset_ms
import com.metrolist.shared.generated.resources.peek_title
import com.metrolist.shared.generated.resources.peek_translation_source_builtin
import com.metrolist.shared.generated.resources.peek_translation_source_google
import com.metrolist.shared.generated.resources.refetch
import com.metrolist.shared.generated.resources.save
import com.metrolist.shared.generated.resources.sources
import com.metrolist.shared.model.LyricsProvider
import com.metrolist.shared.model.LyricsProviderResult
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.previewAspectRatio
import com.metrolist.shared.model.previewThumbnailUrl
import com.metrolist.shared.peek.PeekBuiltInTranslations
import com.metrolist.shared.peek.PeekPlayerMenu
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.displayName
import com.metrolist.shared.ui.components.ExpressiveSwitch
import com.metrolist.shared.ui.components.Material3MenuGroup
import com.metrolist.shared.ui.components.Material3MenuItemData
import com.metrolist.shared.ui.components.ModalBottomSheet
import com.metrolist.shared.ui.components.PlayerMenuAction
import com.metrolist.shared.ui.components.PlayerMenuPrimaryActions
import com.metrolist.shared.ui.components.PopupVisibility
import com.metrolist.shared.ui.components.lyrics.LyricsEditorScreen
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.theme.ComponentShapes
import com.metrolist.shared.ui.utils.setText
import com.metrolist.shared.ui.utils.verticalScrollWithDrag
import com.metrolist.shared.viewmodels.LyricsUiState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsMenu(
    song: SongItem,
    lyricsUiState: LyricsUiState,
    onDismiss: () -> Unit,
    onRefetch: (customTitle: String?, customArtist: String?) -> Unit,
    onLoadProviders: (customTitle: String?, customArtist: String?, provider: LyricsProvider?) -> Unit,
    onSelectProvider: (LyricsProviderResult) -> Unit,
    onShowLyricsOffset: () -> Unit,
    onLyricsChanged: () -> Unit,
    onManualAiTranslationChange: ((Boolean) -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    var editorSong by remember { mutableStateOf(song.id to song.title) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showProviderDialog by remember(song.id) { mutableStateOf(false) }
    var showSearchDialog by remember(song.id) { mutableStateOf(false) }
    var showRefreshMenu by remember(song.id) { mutableStateOf(false) }
    var providerDialogFromRefetch by remember(song.id) { mutableStateOf(false) }

    val queryTitle = lyricsUiState.queryTitle ?: song.title
    val queryArtist = lyricsUiState.queryArtist ?: song.artists.joinToString { it.name }
    var editText by remember(lyricsUiState.rawLyrics) { mutableStateOf(TextFieldValue(lyricsUiState.rawLyrics.orEmpty())) }
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMessage = stringResource(Res.string.copied)
    val romanizeAsMain = Settings.shared.isLyricsRomanizationActive && Settings.shared.lyricsRomanizeAsMain
    val toggleChecked =
        if (romanizeAsMain) {
            Settings.shared.lyricsShowOriginalLyrics
        } else {
            Settings.shared.lyricsRomanizationEnabled
        }
    // PeekMusic: the Peek switch and one translation switch instead of upstream's AI items, as on Android.
    var peekOn by remember { mutableStateOf(Settings.shared.showLyricsOnPlayer) }
    var translationsOn by remember {
        mutableStateOf(Settings.shared.lyricsAiTranslationEnabled && Settings.shared.lyricsShowTranslations)
    }
    val hasTranslations =
        lyricsUiState.lyrics.any { !it.translatedText.isNullOrBlank() || !it.backgroundTranslatedText.isNullOrBlank() }
    val builtInTranslationSource = remember(lyricsUiState.rawLyrics) { PeekBuiltInTranslations.builtInSource(lyricsUiState.rawLyrics) }

    fun setTranslations(enabled: Boolean) {
        translationsOn = enabled
        Settings.shared.lyricsShowTranslations = enabled
        if (enabled) {
            Settings.shared.lyricsAiTranslationEnabled = true
            // Nothing translated yet, e.g. after a failure: translate now.
            if (!hasTranslations) onManualAiTranslationChange?.invoke(true)
        }
    }

    PopupVisibility(visible = showEditDialog) {
        LyricsEditorScreen(
            songId = editorSong.first,
            songTitle = editorSong.second,
            rawLyrics = editText.text,
            onChanged = onLyricsChanged,
            onDismiss = {
                showEditDialog = false
                onDismiss()
            },
        )
    }

    PopupVisibility(visible = showSearchDialog) {
        LyricsSearchDialog(
            initialTitle = queryTitle,
            initialArtist = queryArtist,
            onDismiss = { showSearchDialog = false },
            onSearch = { title, artist ->
                onLoadProviders(title, artist, null)
                showSearchDialog = false
                providerDialogFromRefetch = false
                showProviderDialog = true
            },
        )
    }

    PopupVisibility(visible = showRefreshMenu) {
        LyricsRefreshDialog(
            onDismiss = { showRefreshMenu = false },
            onAutomatic = {
                onRefetch(queryTitle, queryArtist)
                onDismiss()
            },
            onProvider = { provider ->
                onLoadProviders(queryTitle, queryArtist, provider)
                providerDialogFromRefetch = true
                showProviderDialog = true
            },
        )
    }

    PopupVisibility(visible = showProviderDialog) {
        LyricsProviderDialog(
            search = lyricsUiState.providerSearch,
            noResultsText = stringResource(Res.string.no_provider_results_found),
            onDismiss = {
                showProviderDialog = false
                if (providerDialogFromRefetch) {
                    providerDialogFromRefetch = false
                    showRefreshMenu = true
                }
            },
            onSelect = { result ->
                onSelectProvider(result)
                providerDialogFromRefetch = false
                showProviderDialog = false
                onDismiss()
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ComponentShapes.BottomSheetShape,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScrollWithDrag(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // PeekMusic: the player and the lyrics page already show the song, see PeekPlayerMenu.
            if (PeekPlayerMenu.SHOW_LYRICS_HEADER) {
                ExpressiveMediaMenuHeader(
                    title = song.title,
                    subtitle = song.artists.joinToString { it.displayName },
                    thumbnail = song.previewThumbnailUrl,
                    thumbnailAspectRatio = song.previewAspectRatio,
                )
            }

            PlayerMenuPrimaryActions(
                actions =
                    listOf(
                        PlayerMenuAction(
                            icon = MetrolistIcons.Outlined.Edit,
                            title = stringResource(Res.string.edit),
                            onClick = {
                                editText = TextFieldValue(LyricsUtils.lyricsForEditing(lyricsUiState.rawLyrics, lyricsUiState.lyrics))
                                editorSong = song.id to song.title
                                showEditDialog = true
                            },
                        ),
                        PlayerMenuAction(
                            icon = MetrolistIcons.Outlined.Cached,
                            title = stringResource(Res.string.refetch),
                            onClick = { showRefreshMenu = true },
                        ),
                        PlayerMenuAction(
                            icon = MetrolistIcons.Outlined.Search,
                            title = stringResource(Res.string.sources),
                            onClick = {
                                showSearchDialog = true
                            },
                        ),
                        // PeekMusic: Copy is a button next to Sources, as on Android.
                        PlayerMenuAction(
                            icon = MetrolistIcons.Outlined.ContentCopy,
                            title = stringResource(Res.string.copy),
                            onClick = {
                                val textToCopy = LyricsUtils.toPlainText(lyricsUiState.lyrics)
                                coroutineScope.launch {
                                    clipboard.setText(textToCopy)
                                    snackbarHostState.showSnackbar(copiedMessage)
                                }
                            },
                        ),
                    ),
            )

            Material3MenuGroup(
                items =
                    listOfNotNull(
                        // PeekMusic: translation is automatic (Google Translate, or the one shipped with the lyrics),
                        // so the per-track AI switch is gone; see the Android LyricsMenu.
                        Material3MenuItemData(
                            title = { Text(stringResource(Res.string.peek_title)) },
                            icon = { Icon(imageVector = MetrolistIcons.Outlined.Lyrics, contentDescription = null) },
                            onClick = {
                                peekOn = !peekOn
                                Settings.shared.showLyricsOnPlayer = peekOn
                            },
                            trailingContent = {
                                ExpressiveSwitch(
                                    checked = peekOn,
                                    onCheckedChange = {
                                        peekOn = it
                                        Settings.shared.showLyricsOnPlayer = it
                                    },
                                )
                            },
                        ),
                        Material3MenuItemData(
                            title = { Text(stringResource(Res.string.lyrics_show_translations)) },
                            description =
                                if (hasTranslations) {
                                    {
                                        Text(
                                            builtInTranslationSource?.let {
                                                stringResource(Res.string.peek_translation_source_builtin, it)
                                            } ?: stringResource(Res.string.peek_translation_source_google),
                                        )
                                    }
                                } else {
                                    null
                                },
                            icon = {
                                Icon(
                                    imageVector = MetrolistIcons.Outlined.Translate,
                                    contentDescription = null,
                                )
                            },
                            onClick = { setTranslations(!translationsOn) },
                            trailingContent = {
                                ExpressiveSwitch(
                                    checked = translationsOn,
                                    onCheckedChange = { setTranslations(it) },
                                )
                            },
                        ),
                        Material3MenuItemData(
                            title = {
                                Text(
                                    stringResource(
                                        if (romanizeAsMain) {
                                            Res.string.lyrics_romanization_show_original
                                        } else {
                                            Res.string.lyrics_romanize_title
                                        },
                                    ),
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = MetrolistIcons.Outlined.Translate,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                if (romanizeAsMain) {
                                    Settings.shared.lyricsShowOriginalLyrics = !toggleChecked
                                } else {
                                    Settings.shared.lyricsRomanizationEnabled = !toggleChecked
                                }
                            },
                            trailingContent = {
                                ExpressiveSwitch(
                                    checked = toggleChecked,
                                    onCheckedChange = {
                                        if (romanizeAsMain) {
                                            Settings.shared.lyricsShowOriginalLyrics = it
                                        } else {
                                            Settings.shared.lyricsRomanizationEnabled = it
                                        }
                                    },
                                )
                            },
                        ),
                        if (lyricsUiState.lyrics.any { it.time > 0L }) {
                            Material3MenuItemData(
                                title = { Text(stringResource(Res.string.lyrics_offset)) },
                                description = {
                                    val sign = if (lyricsUiState.timingOffsetMs > 0) "+" else ""
                                    Text(sign + stringResource(Res.string.offset_ms, lyricsUiState.timingOffsetMs))
                                },
                                icon = {
                                    Icon(
                                        imageVector = MetrolistIcons.Outlined.FastForward,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    onShowLyricsOffset()
                                },
                            )
                        } else {
                            null
                        },
                    ),
            )

            SnackbarHost(snackbarHostState)
            Spacer(Modifier.height(24.dp))
        }
    }
}
