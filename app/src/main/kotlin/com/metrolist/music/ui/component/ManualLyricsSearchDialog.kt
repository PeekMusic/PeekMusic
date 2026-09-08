/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.app.SearchManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.viewmodels.LyricsMenuViewModel

/**
 * Manual lyrics search: title/artist input dialog followed by a provider-grouped
 * result list. Shared by the lyrics menu and the player lyrics peek.
 * [onDismiss] hides this component (host-controlled visibility).
 * [onHostDismiss] dismisses the surrounding surface — the lyrics menu closes when the
 * user picks a result or searches online; the peek passes a no-op.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualLyricsSearchDialog(
    mediaMetadata: MediaMetadata,
    onDismiss: () -> Unit,
    onHostDismiss: () -> Unit = {},
    viewModel: LyricsMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current

    var showSearchResultDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val (titleField, onTitleFieldChange) =
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(text = mediaMetadata.title))
        }
    val (artistField, onArtistFieldChange) =
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(text = mediaMetadata.artists.joinToString { it.name }))
        }

    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsStateWithLifecycle()

    DefaultDialog(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        onDismiss = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(R.string.search_lyrics)) },
        buttons = {
            TextButton(
                onClick = { onDismiss() },
            ) {
                Text(stringResource(android.R.string.cancel))
            }

            Spacer(Modifier.width(8.dp))

            TextButton(
                onClick = {
                    onHostDismiss()
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_WEB_SEARCH).apply {
                                putExtra(
                                    SearchManager.QUERY,
                                    "${artistField.text} ${titleField.text} lyrics",
                                )
                            },
                        )
                    } catch (_: Exception) {
                    }
                },
            ) {
                Text(stringResource(R.string.search_online))
            }

            Spacer(Modifier.width(8.dp))

            TextButton(
                onClick = {
                    // Try search regardless of network status indicator
                    // as it might be a false negative
                    viewModel.search(
                        mediaMetadata.id,
                        titleField.text,
                        artistField.text,
                        mediaMetadata.duration,
                        mediaMetadata.album?.title,
                    )
                    showSearchResultDialog = true

                    // Show warning only if network is definitely unavailable
                    if (!isNetworkAvailable) {
                        Toast.makeText(context, context.getString(R.string.error_no_internet), Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        OutlinedTextField(
            value = titleField,
            onValueChange = onTitleFieldChange,
            singleLine = true,
            label = { Text(stringResource(R.string.song_title)) },
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = artistField,
            onValueChange = onArtistFieldChange,
            singleLine = true,
            label = { Text(stringResource(R.string.song_artists)) },
        )
    }

    if (showSearchResultDialog) {
        val results by viewModel.results.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

        var expandedText by rememberSaveable {
            mutableStateOf<String?>(null)
        }
        var expandedProviders by rememberSaveable {
            mutableStateOf(listOf<String>())
        }
        // Expand the first provider's results by default once they arrive
        LaunchedEffect(results) {
            if (expandedProviders.isEmpty() && results.isNotEmpty()) {
                expandedProviders = listOf(results.first().providerName)
            }
        }

        // Grouped by provider (insertion order preserved) so providers that
        // return multiple texts (e.g. KuGou) don't blow up the list
        val groupedResults = results.groupBy { it.providerName }

        ListDialog(
            onDismiss = { showSearchResultDialog = false },
        ) {
            groupedResults.forEach { (providerName, providerResults) ->
                val expanded = providerName in expandedProviders
                item(key = "header_$providerName") {
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedProviders =
                                    if (expanded) expandedProviders - providerName
                                    else expandedProviders + providerName
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = providerName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (providerResults.any { it.lyrics.startsWith("[") }) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier =
                                Modifier
                                    .padding(end = 8.dp)
                                    .size(18.dp),
                            )
                        }
                        Text(
                            text = providerResults.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Icon(
                            painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (expanded) {
                    itemsIndexed(
                        providerResults,
                        key = { index, result -> "${providerName}_${index}_${result.lyrics.hashCode()}" },
                    ) { _, result ->
                        Row(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onHostDismiss()
                                    viewModel.cancelSearch()
                                    database.query {
                                        upsert(
                                            LyricsEntity(
                                                id = mediaMetadata.id,
                                                lyrics = result.lyrics,
                                                provider = result.providerName,
                                            ),
                                        )
                                    }
                                }
                                .padding(horizontal = 12.dp)
                                .padding(start = 16.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = result.lyrics,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = if (expandedText == result.lyrics) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (result.lyrics.startsWith("[")) {
                                        Icon(
                                            painter = painterResource(R.drawable.sync),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier =
                                            Modifier
                                                .padding(end = 4.dp)
                                                .size(18.dp),
                                        )
                                    }
                                    if (LyricsUtils.isWordSynced(result.lyrics)) {
                                        Text(
                                            text = stringResource(R.string.lyrics_word_synced),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    expandedText = if (expandedText == result.lyrics) null else result.lyrics
                                },
                            ) {
                                Icon(
                                    painter = painterResource(if (expandedText == result.lyrics) R.drawable.expand_less else R.drawable.expand_more),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (!isLoading && results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        textAlign = TextAlign.Center,
                        modifier =
                        Modifier
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}
