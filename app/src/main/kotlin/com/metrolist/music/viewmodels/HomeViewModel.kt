/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.innertube.models.filterVideoSongs
import com.metrolist.innertube.models.filterYoutubeShorts
import com.metrolist.innertube.pages.ExplorePage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.utils.completed
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DEFAULT_HOME_SECTION_ORDER
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.HideYoutubeShortsKey
import com.metrolist.music.constants.HiddenYouTubeHomeSectionsKey
import com.metrolist.music.constants.HomeSectionOrderKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.QuickPicks
import com.metrolist.music.constants.QuickPicksKey
import com.metrolist.music.constants.ShowWrappedCardKey
import com.metrolist.music.constants.WrappedSeenKey
import com.metrolist.music.constants.effectiveHomeSectionOrder
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.extensions.filterVideoSongs
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.models.SimilarRecommendation
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.ui.screens.wrapped.WrappedAudioService
import com.metrolist.music.ui.screens.wrapped.WrappedManager
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.safeDataStoreEdit
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

internal fun buildSpeedDialItems(
    pinned: List<YTItem>,
    keepListening: List<YTItem>,
    quickPicks: List<YTItem>,
    home: List<YTItem>,
): List<YTItem> =
    (pinned + keepListening + quickPicks + home)
        .distinctBy { it.id }
        .take(27)

// Minimum number of YouTube sections to load regardless of settings.
private const val MIN_HOME_YOUTUBE_SECTIONS = 4

// App-internal section categories visible per login state. Used to derive how many
// YouTube sections need to be loaded to reach the number of enabled sections.
private val INTERNAL_CATEGORIES_LOGGED_IN = setOf("speed_dial", "account_mixes")
private val INTERNAL_CATEGORIES_LOGGED_OUT = setOf(
    "speed_dial",
    "app_internal",
)

private fun targetHomeYouTubeSections(context: Context, loggedIn: Boolean): Int {
    val internalCategories = if (loggedIn) INTERNAL_CATEGORIES_LOGGED_IN else INTERNAL_CATEGORIES_LOGGED_OUT
    val storedOrder = effectiveHomeSectionOrder(
        context.dataStore.get(HomeSectionOrderKey, DEFAULT_HOME_SECTION_ORDER)
    )
    val hiddenCategories = context.dataStore.get(HiddenYouTubeHomeSectionsKey, emptySet<String>())

    // Count sections the user has actually enabled in settings plus the internal sections
    // that are always shown for the current login state.
    val enabledCategories = (storedOrder.filter { it !in hiddenCategories } + internalCategories).distinct()
    val targetTotal = enabledCategories.size.coerceAtLeast(6)
    return (targetTotal - internalCategories.size).coerceAtLeast(MIN_HOME_YOUTUBE_SECTIONS)
}

// Initial home screen loading window: the loading animation shows for at least
// MIN_HOME_LOADING_MS (so sections don't pop in one by one) and at most
// MAX_HOME_LOADING_MS, even if background sections are still loading.
private const val MIN_HOME_LOADING_MS = 1200L
private const val MAX_HOME_LOADING_MS = 2500L

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val wrappedManager: WrappedManager,
    private val wrappedAudioService: WrappedAudioService,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    // True only during the initial load on app start — gates the loading animation so the
    // fully loaded home screen is revealed at once instead of section by section.
    val showInitialLoading = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)

    // Live variants that stay in sync with the song table (likes etc.) with one
    // Room flow per section instead of one flow per visible item.
    @OptIn(ExperimentalCoroutinesApi::class)
    val quickPicksLive: StateFlow<List<Song>?> =
        quickPicks.flatMapLatest { list ->
            if (list == null) {
                flowOf(null)
            } else {
                database.songsByIdsFlow(list.map { it.id }).map { songs ->
                    val byId = songs.associateBy { it.id }
                    list.mapNotNull { byId[it.id] }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val forgottenFavoritesLive: StateFlow<List<Song>?> =
        forgottenFavorites.flatMapLatest { list ->
            if (list == null) {
                flowOf(null)
            } else {
                database.songsByIdsFlow(list.map { it.id }).map { songs ->
                    val byId = songs.associateBy { it.id }
                    list.mapNotNull { byId[it.id] }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    // Official API data for podcast sections
    val savedPodcastShows = MutableStateFlow<List<com.metrolist.innertube.models.PodcastItem>>(emptyList())
    val episodesForLater = MutableStateFlow<List<SongItem>>(emptyList())

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val pinnedSpeedDialItems: StateFlow<List<SpeedDialItem>> =
        database.speedDialDao.getAll()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks,
            homePage,
        ) { pinned, keepListening, quick, home ->
            buildSpeedDialItems(
                pinned = pinned.map { it.toYTItem() },
                keepListening =
                    keepListening.orEmpty().mapNotNull { item ->
                        when (item) {
                            is Song ->
                                SongItem(
                                    id = item.id,
                                    title = item.title,
                                    artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                    thumbnail = item.thumbnailUrl ?: "",
                                )

                            is Album ->
                                AlbumItem(
                                    browseId = item.id,
                                    playlistId = item.album.playlistId ?: "",
                                    title = item.title,
                                    artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                    year = item.album.year,
                                    thumbnail = item.thumbnailUrl ?: "",
                                )

                            is com.metrolist.music.db.entities.Artist ->
                                ArtistItem(
                                    id = item.id,
                                    title = item.title,
                                    thumbnail = item.thumbnailUrl,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                )

                            else -> null
                        }
                    },
                quickPicks =
                    quick.orEmpty().map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                        )
                    },
                home = home?.sections.orEmpty().flatMap { it.items },
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            // Visual feedback for the animation
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        is com.metrolist.music.db.entities.Artist -> otherSources.add(ArtistItem(
                            id = item.id,
                            title = item.title,
                            thumbnail = item.thumbnailUrl,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            // Probability: 80% User Songs, 20% Other Sources
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

	val showWrappedCard: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        val showWrappedPref = prefs[ShowWrappedCardKey] ?: false
        val seen = prefs[WrappedSeenKey] ?: false
        val isBeforeDate = LocalDate.now().isBefore(LocalDate.of(2026, 2, 1))

        isBeforeDate && (!seen || showWrappedPref)
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val wrappedSeen: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[WrappedSeenKey] ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun markWrappedAsSeen() {
        viewModelScope.launch(Dispatchers.IO) {
            context.safeDataStoreEdit {
                it[WrappedSeenKey] = true
            }
        }
    }
    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) return

        val seeds = likedSongs.shuffled().distinctBy { it.id }.take(5)

        // Use a synchronized list to collect results safely from concurrent coroutines
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (item.explicit) return@filter false
                                    true
                                }
                                .shuffled()

                            // Simple check to avoid immediate duplicate of seed
                            val recommendation = recommendations.firstOrNull { rec ->
                                rec.id != seed.id
                            }

                            if (recommendation != null) {
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }
        
        // Final deduplication just in case multiple seeds recommended the same song
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                // Get similar songs from YouTube based on recent listening
                val recentSong = database.latestEvent().first()?.song
                val ytSimilarSongs = mutableListOf<Song>()

                if (recentSong != null) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            // Convert YouTube songs to local Song format if they exist in database
                            val candidates = page.songs.take(10)
                            val localById = database.getSongsByIds(candidates.map { it.id }).associateBy { it.id }
                            candidates.forEach { ytSong ->
                                localById[ytSong.id]?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
                    }
                }

                // Combine all sources and remove duplicates
                val combined = (relatedSongs + forgotten + ytSimilarSongs)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(20)

                quickPicks.value = combined.ifEmpty { relatedSongs.shuffled().take(20) }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.latestEvent().first()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = LocalDateTime.now().minusWeeks(4)
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 5, offset = 0, toTimeStamp = LocalDateTime.now()).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    YouTube.artist(seed.id).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    playlist.author?.name != seed.artist.name &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
            
            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            // Use song count from the playlist page if available, otherwise use original
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
    }

    private suspend fun load() {
        isLoading.value = true
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        val fromTimeStamp = LocalDateTime.now().minusWeeks(2)
        val loadStartMs = System.currentTimeMillis()

        val loggedIn = "SAPISID" in parseCookieString(YouTube.cookie.orEmpty())
        // App-generated sections are always shown when logged out. When logged in, only
        // Quick Picks and account-specific sections remain; the rest is hidden.
        val showInternalSections = !loggedIn

        // Phase 1: Load essential sections in parallel — local DB (fast) + YouTube home page.
        coroutineScope {
            launch(Dispatchers.IO) {
                val firstPage = YouTube.home().getOrNull()
                    ?: run {
                        reportException(IllegalStateException("YouTube.home() returned no page"))
                        return@launch
                    }
                // Pull enough YouTube sections so that, together with the app-generated
                // sections, the home screen reaches the number of sections enabled in settings.
                val targetYouTubeSections = targetHomeYouTubeSections(context, loggedIn)
                var combined = firstPage
                while (combined.sections.size < targetYouTubeSections && combined.continuation != null) {
                    val next = YouTube.home(combined.continuation).getOrNull()
                    if (next == null) break
                    combined = next.copy(
                        chips = combined.chips,
                        sections = combined.sections + next.sections,
                        continuation = next.continuation,
                    )
                }
                homePage.value = combined.copy(
                    sections = combined.sections.mapNotNull { section ->
                        val filtered = section.items
                            .filterOutNulls()
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .filterYoutubeShorts(hideYoutubeShorts)
                        if (filtered.isEmpty()) null else section.copy(items = filtered)
                    }
                )
            }

            if (YouTube.cookie != null) {
                launch(Dispatchers.IO) { loadAccountInfo() }
                launch(Dispatchers.IO) { loadAccountPlaylists() }
            }

            if (showInternalSections) {
                launch(Dispatchers.IO) { getQuickPicks() }

                launch(Dispatchers.IO) {
                    forgottenFavorites.value = database.forgottenFavorites().first()
                        .filterVideoSongs(hideVideoSongs).shuffled().take(20)
                }

                launch(Dispatchers.IO) {
                    val songs = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 15, offset = 5, toTimeStamp = LocalDateTime.now()).first()
                        .filterVideoSongs(hideVideoSongs).shuffled().take(10)
                    val albums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
                        .filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                    val artists = database.mostPlayedArtists(fromTimeStamp).first()
                        .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
                    keepListening.value = (songs + albums + artists).shuffled()
                }
            }
        }

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }

        // Phase 2: Heavy multi-request operations — run in background without blocking the UI.
        if (showInternalSections) {
            viewModelScope.launch(Dispatchers.IO) { getDailyDiscover() }
        }

        val phase2Jobs = listOf(
            viewModelScope.launch(Dispatchers.IO) { getCommunityPlaylists() },
            viewModelScope.launch(Dispatchers.IO) {
                YouTube.explore().onSuccess { page ->
                    explorePage.value = page.copy(
                        newReleaseAlbums = page.newReleaseAlbums.filterOutNulls().filterExplicit(hideExplicit),
                        moodAndGenres = page.moodAndGenres.filterOutNulls()
                    )
                }.onFailure { reportException(it) }
            },
            viewModelScope.launch(Dispatchers.IO) {
                val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(4)
                .mapNotNull {
                    val items = mutableListOf<YTItem>()
                    YouTube.artist(it.id).onSuccess { page ->
                        page.sections.takeLast(3).forEach { section -> items += section.items }
                    }
                    SimilarRecommendation(
                        title = it,
                        items = items
                            .distinctBy { item -> item.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled().take(12)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val songRecommendations = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 15, offset = 0, toTimeStamp = LocalDateTime.now()).first()
                .filter { it.album != null }
                .shuffled().take(3)
                .mapNotNull { song ->
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                        ?: return@mapNotNull null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                    SimilarRecommendation(
                        title = song,
                        items = (page.songs.shuffled().take(10) +
                                page.albums.shuffled().take(5) +
                                page.artists.shuffled().take(3) +
                                page.playlists.shuffled().take(3))
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val albumRecommendations = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                .filter { it.album.thumbnailUrl != null }
                .shuffled().take(2)
                .mapNotNull { album ->
                    val items = mutableListOf<YTItem>()
                    YouTube.album(album.id).onSuccess { page ->
                        page.otherVersions.let { items += it }
                    }
                    album.artists.firstOrNull()?.id?.let { artistId ->
                        YouTube.artist(artistId).onSuccess { page ->
                            page.sections.lastOrNull()?.items?.let { items += it }
                        }
                    }
                    SimilarRecommendation(
                        title = album,
                        items = items
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled().take(10)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            similarRecommendations.value = (artistRecommendations + songRecommendations + albumRecommendations).shuffled()
            allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                    homePage.value?.sections?.flatMap { it.items }.orEmpty()
            },
        )

        // Reveal the home screen only once the background sections have arrived (and at least
        // MIN_HOME_LOADING_MS have passed), or at MAX_HOME_LOADING_MS at the latest — so the
        // sections appear at once instead of popping in one by one.
        viewModelScope.launch {
            val elapsed = System.currentTimeMillis() - loadStartMs
            withTimeoutOrNull((MAX_HOME_LOADING_MS - elapsed).coerceAtLeast(0)) {
                val minRemaining = (MIN_HOME_LOADING_MS - elapsed).coerceAtLeast(0)
                if (minRemaining > 0) delay(minRemaining)
                phase2Jobs.forEach { it.join() }
            }
            isLoading.value = false
            showInitialLoading.value = false
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextSections.sections).mapNotNull { section ->
                    val filteredItems = section.items
                        .filterOutNulls()
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.mapNotNull { section ->
                    val filtered = section.items
                        .filterOutNulls()
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                    section.copy(items = filtered)
                }
            )
            selectedChip.value = chip

            // Fetch podcast-specific data when podcasts chip is selected
            if (chip.title.contains("Podcast", ignoreCase = true)) {
                fetchPodcastData()
            }
        }
    }

    private suspend fun fetchPodcastData() {
        // Fetch saved podcast shows from official API
        YouTube.savedPodcastShows().onSuccess { shows ->
            savedPodcastShows.value = shows.filterOutNulls()
        }.onFailure {
            reportException(it)
        }

        // Fetch episodes for later from official API
        YouTube.episodesForLater().onSuccess { episodes ->
            episodesForLater.value = episodes.filterOutNulls()
        }.onFailure {
            reportException(it)
        }
    }

    /**
     * Loads the account name/avatar for consumers outside the home screen (e.g. the top app bar
     * in MainActivity, which uses its own activity-scoped instance of this ViewModel and never
     * runs [loadHomeData]).
     */
    fun refreshAccountInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val cookie = context.dataStore.data.map { it[InnerTubeCookieKey] }.first()
            if (cookie.isNullOrEmpty()) return@launch
            YouTube.cookie = cookie
            loadAccountInfo()
        }
    }

    private suspend fun loadAccountInfo() {
        YouTube.accountInfo().onSuccess { info ->
            accountName.value = info.name
            accountImageUrl.value = info.thumbnailUrl
        }.onFailure {
            reportException(it)
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterOutNulls()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    /**
     * Safely filters out null items from a list whose type says non-null
     * but may contain nulls at runtime due to JSON parsing.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> List<T>.filterOutNulls(): List<T> =
        (this as List<T?>).filterNotNull()

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // If a chip is selected, reload the chip's content instead of the default home
            val currentChip = selectedChip.value
            if (currentChip != null) {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                val nextSections = YouTube.home(params = currentChip.endpoint?.params).getOrNull()
                if (nextSections != null) {
                    homePage.value = nextSections.copy(
                        chips = homePage.value?.chips,
                        sections = nextSections.sections.mapNotNull { section ->
                            val filtered = section.items
                                .filterOutNulls()
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .filterYoutubeShorts(hideYoutubeShorts)
                            section.copy(items = filtered)
                        }
                    )
                }
            } else {
                load()
            }
            isRefreshing.value = false
        }
        // Run sync when user manually refreshes
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    override fun onCleared() {
        super.onCleared()
        wrappedManager.dispose()
    }

    init {
        // Run sync in separate coroutine with cooldown to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }

        var wasOffline = !networkConnectivity.networkStatus.value
        viewModelScope.launch(Dispatchers.IO) {
            networkConnectivity.networkStatus.collect { isConnected ->
                if (!isConnected) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    refresh()
                }
            }
        }

        // Prepare wrapped data in background
        viewModelScope.launch(Dispatchers.IO) {
            showWrappedCard.collect { shouldShow ->
                if (shouldShow && !wrappedManager.state.value.isDataReady) {
                    try {
                        wrappedManager.prepare()
                        val state = wrappedManager.state.first { it.isDataReady }
                        val trackMap = state.trackMap
                        if (trackMap.isNotEmpty()) {
                            val firstTrackId = trackMap.entries.first().value
                            wrappedAudioService.prepareTrack(firstTrackId)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
        }

        // Listen for cookie changes and reload account data
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] to it[AccountNameKey] }
                .distinctUntilChanged()
                .collect { (cookie, savedAccountName) ->
                    if (!cookie.isNullOrEmpty()) {
                        YouTube.cookie = cookie
                        accountName.value = savedAccountName.orEmpty().ifBlank { "Guest" }
                        loadAccountInfo()
                    } else {
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                        accountPlaylists.value = null
                    }
                }
        }

        // Listen for HideYoutubeShorts preference changes and reload account playlists instantly
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }

    private var isHomeDataLoaded = false

    fun loadHomeData() {
        if (isHomeDataLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cookie = context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .first()

                if (!cookie.isNullOrEmpty()) {
                    YouTube.cookie = cookie
                }

                isHomeDataLoaded = true
                showInitialLoading.value = true
                load()
            } catch (e: Exception) {
                isHomeDataLoaded = false
                Timber.e(e, "Failed to load home data")
            }
        }
    }
}
