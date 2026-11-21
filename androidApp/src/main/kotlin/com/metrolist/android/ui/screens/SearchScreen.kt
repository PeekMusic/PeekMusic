/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Interactive Search Screen - Complete stuff/app implementation with navigation and actions
 */

package com.metrolist.android.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.android.LocalMiniPlayerBottomInset
import com.metrolist.android.LocalPlayerExpanded
import com.metrolist.android.LocalPlaylistAdder
import com.metrolist.android.extensions.findActivity
import com.metrolist.android.playback.LocalPlayerConnection
import com.metrolist.android.ui.component.SwipeGestureHandler
import com.metrolist.android.ui.menu.SongMenu
import com.metrolist.android.ui.utils.playSong
import com.metrolist.android.utils.startActivitySafely
import com.metrolist.android.viewmodels.SearchViewModel
import com.metrolist.innertubex.utils.YouTubeUrlParser
import com.metrolist.shared.api.playlistSongs
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.recognize_music
import com.metrolist.shared.generated.resources.search_history_removal_failed
import com.metrolist.shared.generated.resources.share_song
import com.metrolist.shared.generated.resources.voice_search_prompt
import com.metrolist.shared.generated.resources.voice_search_unavailable
import com.metrolist.shared.generated.resources.youtube_music
import com.metrolist.shared.model.AlbumItem
import com.metrolist.shared.model.ArtistItem
import com.metrolist.shared.model.ArtistTiny
import com.metrolist.shared.model.EpisodeItem
import com.metrolist.shared.model.PlaylistItem
import com.metrolist.shared.model.PodcastItem
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.model.YTItem
import com.metrolist.shared.model.toSongItem
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.state.GlobalYouTubeRepository
import com.metrolist.shared.state.SongLikeState
import com.metrolist.shared.ui.LocalIsTv
import com.metrolist.shared.ui.components.CollectionPreviews
import com.metrolist.shared.ui.components.PopupContent
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.menu.rememberSongWithAlbum
import com.metrolist.shared.ui.navigation.Navigator
import com.metrolist.shared.ui.navigation.PredictiveNavigationContent
import com.metrolist.shared.ui.navigation.Screen
import com.metrolist.shared.ui.screens.SearchScreen
import com.metrolist.shared.ui.screens.searchScreenText
import com.metrolist.shared.utils.ShareUrls
import com.metrolist.shared.utils.parseYouTubeStartPositionMs
import com.metrolist.shared.utils.parseYouTubeUrl
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navigator: Navigator,
    floatingActionsBottomInset: State<Dp>,
    initialQuery: String? = null,
    onInitialQueryConsumed: () -> Unit = {},
    onSearchResultsVisible: (Boolean) -> Unit = {},
    searchViewModel: SearchViewModel = koinViewModel(),
) {
    val voiceSearchPromptText = stringResource(Res.string.voice_search_prompt)
    val voiceSearchUnavailableText = stringResource(Res.string.voice_search_unavailable)
    val shareSongText = stringResource(Res.string.share_song)

    val searchUiState by searchViewModel.shared.uiState.collectAsState()
    val query = searchUiState.query
    val isQuerySubmitted = searchUiState.isQuerySubmitted
    var isResultsPage by rememberSaveable { mutableStateOf(isQuerySubmitted) }
    // Editing a submitted query clears isQuerySubmitted; only explicit Back leaves the results page.
    LaunchedEffect(isQuerySubmitted) { if (isQuerySubmitted) isResultsPage = true }
    val selectedFilterState = searchUiState.selectedFilter

    LaunchedEffect(isResultsPage) {
        onSearchResultsVisible(isResultsPage)
    }

    val playerConnection = LocalPlayerConnection.current
    val currentSong by playerConnection?.currentSong?.collectAsState() ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val itemContainer: @Composable (YTItem, @Composable () -> Unit) -> Unit = { item, content ->
        if (item is SongItem) {
            SwipeGestureHandler(
                onSwipeLeft = { playerConnection?.addToQueue(item) },
                onSwipeRight = { playerConnection?.addToQueue(item, playNext = true) },
                enabled = playerConnection != null && Settings.shared.swipeToSong,
                content = content,
            )
        } else {
            content()
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historyRemovalFailed = stringResource(Res.string.search_history_removal_failed)
    LaunchedEffect(searchUiState.historyMutationFailures) {
        if (searchUiState.historyMutationFailures > 0) Toast.makeText(context, historyRemovalFailed, Toast.LENGTH_LONG).show()
    }

    val addToPlaylist = LocalPlaylistAdder.current
    var showSongMenu by remember { mutableStateOf<SongItem?>(null) }
    // PeekMusic: albums, artists, playlists and podcasts get the same ⋮ menus as on Home.
    var collectionMenuItem by remember { mutableStateOf<YTItem?>(null) }

    val miniPlayerInset = LocalMiniPlayerBottomInset.current
    val isTv = LocalIsTv.current
    val density = LocalDensity.current
    val fullWindowWidth =
        with(density) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    val safeStartPadding =
        WindowInsets.safeDrawing
            .asPaddingValues()
            .calculateStartPadding(LocalLayoutDirection.current)
    val imeBottomInset = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val isImeVisible = imeBottomInset > 0.dp
    val bottomOverlayInset = maxOf(imeBottomInset, miniPlayerInset)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val voiceSearchEnabled = Settings.shared.voiceSearchEnabled
    val isPlayerExpanded = LocalPlayerExpanded.current
    var showFloatingAction by remember { mutableStateOf(true) }

    val dismissKeyboardAndFocus = {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    DisposableEffect(searchViewModel, context) {
        onDispose {
            if (context.findActivity()?.isChangingConfigurations != true) {
                searchViewModel.hasRequestedInitialFocus = false
            }
            dismissKeyboardAndFocus()
            onSearchResultsVisible(false)
        }
    }

    val fabNestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        when {
                            available.y < -1f -> showFloatingAction = false
                            available.y > 1f -> showFloatingAction = true
                        }
                    }
                    return Offset.Zero
                }
            }
        }

    val returnToMainSearch = {
        dismissKeyboardAndFocus()
        isResultsPage = false
        searchViewModel.hasRequestedInitialFocus = false
        selectedFilterState?.let { searchViewModel.shared.clearFilter() }
        searchViewModel.shared.resetSearch()
    }

    LaunchedEffect(Unit) {
        searchViewModel.shared.refreshSearchHistory()
    }

    val routeYouTubeUrl: (String) -> Boolean = { text ->
        dismissKeyboardAndFocus()
        when (val parsed = parseYouTubeUrl(text)) {
            null -> {
                false
            }

            is YouTubeUrlParser.ParsedUrl.Video -> {
                scope.launch {
                    val info =
                        runCatching {
                            GlobalYouTubeRepository.instance.getSongPlayerInformation(parsed.id)
                        }.getOrNull()
                    val song =
                        SongItem(
                            id = parsed.id,
                            title = info?.title ?: parsed.id,
                            thumbnail = info?.thumbnail,
                            artists = listOf(ArtistTiny(id = null, name = info?.artist ?: getString(Res.string.youtube_music))),
                            album = null,
                            duration = info?.durationSeconds,
                        )
                    playerConnection?.playSong(song, "", startPositionMs = parseYouTubeStartPositionMs(text))
                }
                searchViewModel.shared.resetSearch()
                true
            }

            is YouTubeUrlParser.ParsedUrl.Playlist -> {
                searchViewModel.shared.resetSearch()
                navigator.navigate(Screen.Playlist(parsed.id))
                true
            }

            is YouTubeUrlParser.ParsedUrl.Album -> {
                searchViewModel.shared.resetSearch()
                navigator.navigate(Screen.Album(parsed.id))
                true
            }

            is YouTubeUrlParser.ParsedUrl.Artist -> {
                searchViewModel.shared.resetSearch()
                navigator.navigate(Screen.Artist(parsed.id))
                true
            }
        }
    }

    val submitSearch: (String) -> Unit = { searchText ->
        val normalized = searchText.trim()
        if (normalized.isNotEmpty()) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            if (!routeYouTubeUrl(normalized)) {
                isResultsPage = true
                searchViewModel.shared.onQueryChange(normalized)
                searchViewModel.shared.searchQuery(normalized)
            }
        }
    }

    val voiceSearchLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.let(submitSearch)
            }
        }

    val launchVoiceSearch = {
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, voiceSearchPromptText)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        if (intent.resolveActivity(context.packageManager) != null) {
            voiceSearchLauncher.launch(intent)
        } else {
            Toast.makeText(context, voiceSearchUnavailableText, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(initialQuery) {
        val queryFromIntent = initialQuery?.trim().orEmpty()
        if (queryFromIntent.isNotEmpty() && queryFromIntent != query) {
            submitSearch(queryFromIntent)
        }
        // Clearing the request lets the same link search again after the query changes.
        if (initialQuery != null) onInitialQueryConsumed()
    }

    LaunchedEffect(isQuerySubmitted, initialQuery, isTv, isPlayerExpanded) {
        if (isTv || isPlayerExpanded) {
            keyboardController?.hide()
        } else if (!isQuerySubmitted && initialQuery.isNullOrBlank()) {
            if (!searchViewModel.hasRequestedInitialFocus && query.isEmpty()) {
                searchViewModel.hasRequestedInitialFocus = true
                focusRequester.requestFocus()
            }
        }
    }

    LaunchedEffect(isQuerySubmitted) {
        if (isQuerySubmitted) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(fabNestedScrollConnection),
            contentAlignment = Alignment.TopCenter,
        ) {
            val searchBarEndPadding =
                if (isResultsPage) {
                    safeStartPadding
                } else {
                    (fullWindowWidth - maxWidth).coerceAtLeast(0.dp)
                }

            PredictiveNavigationContent(
                targetState = isResultsPage,
                previousState = false,
                backEnabled = isResultsPage,
                onBack = returnToMainSearch,
                modifier = Modifier.fillMaxSize(),
            ) { submitted ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .widthIn(max = 1400.dp)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .then(
                                if (isResultsPage) {
                                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    SearchScreen(
                        uiState =
                            if (submitted || !isResultsPage) {
                                searchUiState
                            } else {
                                searchUiState.copy(
                                    query = "",
                                    isQuerySubmitted = false,
                                    hasSearched = false,
                                    searchSuggestions = null,
                                )
                            },
                        text = searchScreenText(),
                        onQueryChange = searchViewModel.shared::onQueryChange,
                        onSearch = submitSearch,
                        onBack = returnToMainSearch,
                        onVideoItemClick = { item ->
                            dismissKeyboardAndFocus()
                            if (item is SongItem) {
                                searchViewModel.shared.addPlayedSearchSong(item)
                                playSong(item, playerConnection, scope, startVideo = true)
                            }
                        },
                        onItemClick = { item ->
                            dismissKeyboardAndFocus()
                            when (item) {
                                is SongItem -> {
                                    searchViewModel.shared.addPlayedSearchSong(item)
                                    playSong(item, playerConnection, scope)
                                }

                                is AlbumItem -> {
                                    navigator.navigate(Screen.Album(item.id))
                                }

                                is ArtistItem -> {
                                    CollectionPreviews.record(item)
                                    navigator.navigate(Screen.Artist(item.id))
                                }

                                is PlaylistItem -> {
                                    navigator.navigate(Screen.Playlist(item.id, thumbnailHint = item.thumbnail))
                                }

                                is PodcastItem -> {
                                    navigator.navigate(Screen.Podcast(item.id))
                                }

                                is EpisodeItem -> {
                                    playSong(item.toSongItem(), playerConnection, scope)
                                }

                                else -> {}
                            }
                        },
                        onAlbumPlay = { album ->
                            dismissKeyboardAndFocus()
                            playerConnection?.playAlbum(album.id, album.title)
                        },
                        onItemLongClick = { item ->
                            when (item) {
                                is SongItem -> showSongMenu = item
                                is AlbumItem, is ArtistItem, is PlaylistItem, is PodcastItem -> collectionMenuItem = item
                                else -> Unit
                            }
                        },
                        onSongSave = { addToPlaylist(listOf(it)) },
                        onHistoryItemClick = submitSearch,
                        onHistoryItemInsert = { historyItem ->
                            searchViewModel.shared.onQueryChange(historyItem)
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                        onRemoveHistoryItem = searchViewModel.shared::removeSearchHistoryItem,
                        onClearHistory = searchViewModel.shared::clearSearchHistory,
                        onRemoveRecentItem = searchViewModel.shared::removeRecentPlayedSearchSong,
                        onClearRecentItems = searchViewModel.shared::clearRecentPlayedSearchSongs,
                        onFilterSelected = { filter ->
                            if (filter == null) searchViewModel.shared.clearFilter() else searchViewModel.shared.searchFiltered(filter)
                        },
                        onLoadMore = searchViewModel.shared::loadMoreFilteredResults,
                        onRetryLoadMore = searchViewModel.shared::retryLoadMoreFilteredResults,
                        currentSongId = currentSong?.id,
                        isPlaying = isPlaying,
                        contentPadding = PaddingValues(bottom = bottomOverlayInset + 24.dp),
                        onArtistClick = { artistId ->
                            dismissKeyboardAndFocus()
                            navigator.navigate(Screen.Artist(artistId))
                        },
                        onAlbumClick = { albumId ->
                            dismissKeyboardAndFocus()
                            navigator.navigate(Screen.Album(albumId))
                        },
                        onShuffleClick = { item ->
                            dismissKeyboardAndFocus()
                            if (item is ArtistItem) {
                                scope.launch {
                                    GlobalYouTubeRepository.instance.getArtistSongs(item.id).takeIf { it.isNotEmpty() }?.let {
                                        playerConnection?.shuffleQueue(it, item.title, songsOnly = true)
                                    }
                                }
                            }
                        },
                        onMixClick = { item ->
                            dismissKeyboardAndFocus()
                            if (item is ArtistItem) {
                                scope.launch {
                                    GlobalYouTubeRepository.instance.startArtistRadio(item.id).takeIf { it.isNotEmpty() }?.let {
                                        playerConnection?.playQueue(it, 0, "${item.title} Radio")
                                    }
                                }
                            }
                        },
                        itemContainer = itemContainer,
                        onMicrophoneClick =
                            if (voiceSearchEnabled) {
                                launchVoiceSearch
                            } else {
                                { navigator.navigate(Screen.Recognition) }
                            },
                        focusRequester = focusRequester,
                        searchBarEndPadding = searchBarEndPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            AnimatedVisibility(
                visible = !isQuerySubmitted && (isTv || showFloatingAction) && !isImeVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = maxOf(imeBottomInset, floatingActionsBottomInset.value) + 16.dp),
            ) {
                FloatingActionButton(
                    onClick = { navigator.navigate(Screen.Recognition) },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = MetrolistIcons.Outlined.AudioCapture,
                        contentDescription = stringResource(Res.string.recognize_music),
                    )
                }
            }
        }
    }

    PopupContent(showSongMenu) { selectedSong ->
        val song = rememberSongWithAlbum(selectedSong)
        SongMenu(
            song = song,
            isLiked = SongLikeState.isLiked(song.id),
            onDismiss = { showSongMenu = null },
            onPlayNext = { playerConnection?.addToQueue(song, playNext = true) },
            onAddToQueue = { playerConnection?.addToQueue(song) },
            onLike = { SongLikeState.toggleLike(song) },
            onShare = {
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, ShareUrls.song(song.id))
                    }
                val chooser = Intent.createChooser(shareIntent, shareSongText)
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivitySafely(chooser)
            },
            onGoToAlbum = song.album?.id?.let { albumId -> { navigator.navigate(Screen.Album(albumId)) } },
            onGoToArtist = { navigator.navigate(Screen.Artist(it)) },
        )
    }

    HomeItemMenu(
        selectedItem = collectionMenuItem,
        onDismiss = { collectionMenuItem = null },
        onNavigateToAlbum = { navigator.navigate(Screen.Album(it)) },
        onNavigateToArtist = { navigator.navigate(Screen.Artist(it)) },
        onNavigateToPodcast = { navigator.navigate(Screen.Podcast(it)) },
        onSongMetadataUpdated = {},
    )
}
