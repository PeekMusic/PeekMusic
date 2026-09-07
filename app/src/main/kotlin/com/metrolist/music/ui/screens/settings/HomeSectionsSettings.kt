/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.DEFAULT_HOME_SECTION_ORDER
import com.metrolist.music.constants.HiddenYouTubeHomeSectionsKey
import com.metrolist.music.constants.HomeSectionOrderKey
import com.metrolist.music.constants.RandomizeHomeOrderKey
import com.metrolist.music.constants.ShowInternalHomeSectionsKey
import com.metrolist.music.constants.effectiveHomeSectionOrder
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private fun categoryLabelRes(category: String): Int =
    when (category) {
        "quick_picks" -> R.string.section_order_quick_picks
        "forgotten_favorites" -> R.string.section_order_forgotten_favorites
        "from_your_library" -> R.string.section_order_from_your_library
        "recommended_mixes" -> R.string.section_order_recommended_mixes
        "recommended_playlists" -> R.string.section_order_recommended_playlists
        "account_mixes" -> R.string.section_order_account_mixes
        "podcasts" -> R.string.section_order_podcasts
        "new_episodes" -> R.string.section_order_new_episodes
        "trending" -> R.string.section_order_trending
        "keep_listening" -> R.string.section_order_keep_listening
        "shows" -> R.string.section_order_shows
        "moods_and_genres" -> R.string.section_order_moods_and_genres
        "recaps" -> R.string.section_order_recaps
        "live_performances" -> R.string.section_order_live_performances
        "music_videos" -> R.string.section_order_music_videos
        "covers_and_remixes" -> R.string.section_order_covers_and_remixes
        "together" -> R.string.section_order_together
        "long_listens" -> R.string.section_order_long_listens
        "similar_to" -> R.string.section_order_similar_to
        "from_the_community" -> R.string.section_order_from_the_community
        "artist" -> R.string.section_order_artist
        "other" -> R.string.section_order_other
        else -> 0
    }

@Composable
private fun homeSettingsSwitchItem(
    icon: Int,
    titleRes: Int,
    descriptionRes: Int?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
): Material3SettingsItem =
    Material3SettingsItem(
        icon = painterResource(icon),
        title = { Text(stringResource(titleRes)) },
        description = descriptionRes?.let { { Text(stringResource(it)) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = {
                    Icon(
                        painter = painterResource(
                            id = if (checked) R.drawable.check else R.drawable.close
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        },
        onClick = { onCheckedChange(!checked) }
    )

/**
 * Home screen settings: the home-related toggles live here, the draggable section order
 * (including hide/restore) is in the subsection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSectionsSettings(
    navController: NavController,
) {
    val (randomizeHomeOrder, onRandomizeHomeOrderChange) = rememberPreference(RandomizeHomeOrderKey, true)
    val (showInternalHomeSections, onShowInternalHomeSectionsChange) =
        rememberPreference(ShowInternalHomeSectionsKey, false)

    Column(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.home_screen_sections),
            items = listOf(
                homeSettingsSwitchItem(
                    icon = R.drawable.shuffle,
                    titleRes = R.string.randomize_home_order,
                    descriptionRes = R.string.randomize_home_order_desc,
                    checked = randomizeHomeOrder,
                    onCheckedChange = onRandomizeHomeOrderChange,
                ),
                homeSettingsSwitchItem(
                    icon = R.drawable.home_outlined,
                    titleRes = R.string.show_app_home_sections,
                    descriptionRes = R.string.show_app_home_sections_desc,
                    checked = showInternalHomeSections,
                    onCheckedChange = onShowInternalHomeSectionsChange,
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.drag_handle),
                    title = { Text(stringResource(R.string.home_section_order)) },
                    enabled = !randomizeHomeOrder,
                    onClick = { navController.navigate("settings/home_sections/order") },
                ),
            )
        )

        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.home_screen_sections)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}

/**
 * Subsection of the home screen settings: order the section categories via drag & drop and
 * hide/restore whole categories with the toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSectionOrderScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val (randomizeHomeOrder) = rememberPreference(RandomizeHomeOrderKey, true)
    val (hiddenCategories, onHiddenCategoriesChange) =
        rememberPreference(HiddenYouTubeHomeSectionsKey, emptySet<String>())
    val (_, onHomeSectionOrderChange) =
        rememberPreference(HomeSectionOrderKey, DEFAULT_HOME_SECTION_ORDER)

    var categories by remember {
        mutableStateOf(
            runBlocking {
                effectiveHomeSectionOrder(
                    context.dataStore.data.first()[HomeSectionOrderKey] ?: DEFAULT_HOME_SECTION_ORDER
                )
            }
        )
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = from.index
        val toIndex = to.index
        if (fromIndex in categories.indices && toIndex in categories.indices) {
            categories = categories.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            onHomeSectionOrderChange(categories.joinToString(","))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun toggleHidden(category: String) {
        onHiddenCategoriesChange(
            if (category in hiddenCategories) hiddenCategories - category
            else hiddenCategories + category
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        if (randomizeHomeOrder) {
            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        title = {},
                        description = {
                            Text(stringResource(R.string.home_section_order_shuffle_hint))
                        },
                        onClick = null
                    )
                )
            )
            Spacer(Modifier.height(16.dp))
        }

        Material3SettingsGroup(
            title = stringResource(R.string.home_section_order),
            items = emptyList(),
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .height((categories.size * 72).dp),
            userScrollEnabled = false,
        ) {
            items(categories, key = { it }) { category ->
                val hidden = category in hiddenCategories
                ReorderableItem(reorderableState, key = category) {
                    Material3SettingsGroup(
                        items = listOf(
                            Material3SettingsItem(
                                title = {
                                    val res = categoryLabelRes(category)
                                    Text(
                                        text = if (res != 0) stringResource(res) else category,
                                        color = if (hidden) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.drag_handle),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .longPressDraggableHandle(
                                                    onDragStarted = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                ),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Switch(
                                            checked = !hidden,
                                            onCheckedChange = { toggleHidden(category) },
                                            thumbContent = {
                                                Icon(
                                                    painter = painterResource(
                                                        if (hidden) R.drawable.close else R.drawable.check
                                                    ),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                                )
                                            }
                                        )
                                    }
                                },
                                onClick = { toggleHidden(category) },
                            )
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.home_section_order)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
